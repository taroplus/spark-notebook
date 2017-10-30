import sys
import traceback
import ast

from time import sleep
from py4j.java_gateway import java_import, JavaGateway, GatewayClient
from py4j.protocol import Py4JError

from pyspark import SparkConf
from pyspark.context import SparkContext
from pyspark.sql import SparkSession, SQLContext

# Connect to the gateway
gateway = JavaGateway(GatewayClient(port=int(sys.argv[1])), auto_convert=True)

# Import the classes used by PySpark
java_import(gateway.jvm, "org.apache.spark.SparkConf")
java_import(gateway.jvm, "org.apache.spark.api.java.*")
java_import(gateway.jvm, "org.apache.spark.api.python.*")
java_import(gateway.jvm, "org.apache.spark.ml.python.*")
java_import(gateway.jvm, "org.apache.spark.mllib.api.python.*")
# TODO(davies): move into sql
java_import(gateway.jvm, "org.apache.spark.sql.*")
java_import(gateway.jvm, "org.apache.spark.sql.hive.*")
java_import(gateway.jvm, "scala.Tuple2")

python_kernel = gateway.entry_point
# auto generated variable counter
var_counter = 0

# class to handle forwarding message to java side
class OutputForwarder:
    def __init__(self):
        self._stdout = sys.stdout
        self._stderr = sys.stderr
        self.request = None
        sys.stdout = self
        sys.stderr = self

    def sysout(self, message):
        self._stdout.write(message)

    def current_request(self, request):
        self.request = request

    def reset(self):
        self.request = None

    def write(self, message):
        if self.request is None:
            self._stdout.write(message)
        else:
            self.request.write(message)

# capture stdout/stderr
output = OutputForwarder()

try:
    import matplotlib
    from mpl import flush_figures, clear_figures
    mpl = True
except Exception:
    output.sysout("Failed to load matplotlib")
    output.sysout(traceback.format_exc())
    mpl = False

# Initialize spark variables
jsc = python_kernel.sparkContext()
sc = SparkContext(jsc = jsc, gateway = gateway, conf = SparkConf(_jvm = gateway.jvm, _jconf = jsc.getConf()))
spark = SparkSession(sc, python_kernel.sparkSession())

# main interpreter loop
while True:
    request = None
    try:
        request = python_kernel.nextExecuteRequest()
        if request is None:
            sleep(1)
            continue
        else:
            output.current_request(request)

        code = None
        for line in request.code().split("\n"):
            if line is None or len(line.strip()) == 0:
                continue
            elif line.strip().startswith("#"):
                continue
            elif code is None:
                code = line
            else:
                code += "\n" + line

        variables = []
        if code:
            parsed = ast.parse(code)
            for i, node in enumerate(parsed.body):
                # do this auto variable only for pure expressions
                # which is not a function call
                if isinstance(node, ast.Expr) and not isinstance(node.value, ast.Call):
                    name = "res%s" % var_counter
                    var_counter += 1
                    new_node = (ast.Assign(targets=[ast.Name(id=name, ctx=ast.Store())], value=node.value))
                    parsed.body[i] = ast.fix_missing_locations(new_node)
                    variables.append(name)

            eval(compile(parsed, "<string>", "exec"))
            for var in variables:
                val = locals()[var]
                from pprint import pprint
                if "DataFrame" in type(val).__name__ or val is not None:
                    pprint(val)

            if mpl: flush_figures(request)
    except Py4JError:
        # this means remote java process is dead, just quit
        break
    except:
        # errors should not be truncated
        output.write(traceback.format_exc())
    finally:
        # clear all pending figures
        if mpl: clear_figures()

    # always complete
    if request is not None:
        request.complete()
    output.reset()
