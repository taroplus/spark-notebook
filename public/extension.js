define([
    "jquery",
    "base/js/namespace",
    "base/js/events"
], function($, IPython, events) {
    var baseDiv = "<div style='border-bottom: 1px solid #464b50; border-top-width: 0; margin: -3px 1px 0 1px; height: 3px'></div>";
    var barDiv = "<div style='float: left; height: 100%; transition: width 0.1s ease;'></div>";

    // load flot / flot.js
    require(['flot'], function(){
        require(['flot.time'], function(){
            console.log("FlotJS has been loaded");
        })
    });

    function comm_open(msg) {
        var cell = IPython.notebook.get_msg_cell(msg.parent_header.msg_id)
        if (cell && cell.element) {
            $(baseDiv).attr("id", msg['content'].comm_id)
                .insertAfter($(cell.element).find(".inner_cell .input_area"))
                .append($(barDiv).css({"width": "0%", "background-color": "rgb(51, 122, 183)"}).addClass("completed"))
                .append($(barDiv).css({"width": "0%", "background-color": "rgba(51, 122, 183, 0.6)"}).addClass("running"));
        }
    }

    function comm_msg(msg) {
        var bar = $("#" + msg.content.comm_id)
        var data = msg.content.data
        var total = data.completed + data.running + data.scheduled;
        if (data.completed > 0) {
            bar.find(".completed").css("width", parseInt(data.completed * 100 / total) + "%")
        }
        if (data.running > 0) {
            bar.find(".running").css("width", parseInt(data.running * 100 / total) + "%")
        }
    }

    function comm_close(msg) {
        $("#" + msg['content'].comm_id).remove();
    }

    function handle_kernel() {
        var kernel = IPython.notebook.kernel
        if (kernel && kernel.comm_manager) {
            kernel.comm_manager.register_target('spark.progress', function(comm, msg) {
                comm.on_msg(comm_msg);
                comm.on_close(comm_close);
                comm_open(msg);
            });
        }
    };

    function load_ipython_extension() {
        // If a kernel already exists, create a widget manager.
        if (IPython.notebook && IPython.notebook.kernel) {
            handle_kernel(IPython, IPython.notebook.kernel);
        }
        // When the kernel is created, create a widget manager.
        events.on('kernel_created.Kernel kernel_created.Session', function(event, data) {
            handle_kernel(IPython, data.kernel);
        });

        /**
         * The views on this page. We keep this list so that we can call the view.remove()
         * method when a view is removed from the page.
         */
        var views = {};
        var removeView = function(event, data) {
            var output = data.cell ? data.cell.output_area : data.output_area;
        }

        // Deleting a cell does *not* clear the outputs first.
        events.on('delete.Cell', removeView);
        // add an event to the notebook element for *any* outputs that are cleared.
        IPython.notebook.container.on('clearing', '.output', removeView);

        events.on('execute.CodeCell', removeView);
        events.on('clear_output.CodeCell', removeView);
    }

    return {load_ipython_extension: load_ipython_extension};
});