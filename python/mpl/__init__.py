"""
 matplotlib backend for sparkling-notebook
"""
import matplotlib
from matplotlib._pylab_helpers import Gcf
from matplotlib.backends.backend_svg import FigureCanvasSVG, FigureManagerSVG
from matplotlib.backend_bases import _Backend

__overrides = {
    'interactive': True,
    'figure.figsize': [6.0, 4.0],
    'font.sans-serif': ['Helvetica', 'Arial', 'sans-serif'],
    'savefig.bbox': 'tight',
    'savefig.dpi': 72,
    # copied from ggplot style
    'patch.linewidth': 0.5,
    'patch.facecolor': '348ABD',
    'patch.edgecolor': 'EEEEEE',
    'patch.antialiased': True,
    'font.size': 9,
    'axes.facecolor': 'E5E5E5',
    'axes.linewidth': 0,
    'axes.grid': True,
    'axes.titlesize': 'x-large',
    'axes.labelsize': 'large',
    'axes.labelcolor': '555555',
    'axes.axisbelow': True,
    'xtick.color': '555555',
    'xtick.direction': 'out',
    'ytick.color': '555555',
    'ytick.direction': 'out',
    'grid.color': 'white',
    'grid.linestyle': '-',
    'savefig.facecolor': 'F0F0F0',
    'savefig.edgecolor': '0.50',
    'legend.facecolor': 'F0F0F0'
}

for k,v in __overrides.items():
    matplotlib.rcParams[k] = v

tracking_managers = []

def flush_figures(request):
    # for some reason this has to be done here.
    matplotlib.rcParams['svg.fonttype'] = 'none'

    try:
        from io import BytesIO
        for manager in tracking_managers:
            fig = manager.canvas.figure
            if fig.axes or fig.lines:
                bytes = BytesIO()
                fig.canvas.print_figure(bytes)
                request.svg(bytes.getvalue().decode('utf-8'))
    finally:
        del tracking_managers[:]
        if hasattr(matplotlib, 'pyplot'):
            matplotlib.pyplot.close('all')


@_Backend.export
class _BackendSparklingNotebook(_Backend):
    FigureCanvas = FigureCanvasSVG
    FigureManager = FigureManagerSVG

    @staticmethod
    def trigger_manager_draw(manager):
        try:
            tracking_managers.remove(manager)
        except:
            pass
        tracking_managers.append(manager)

    @staticmethod
    def show():
        managers = Gcf.get_all_fig_managers()
        if not managers:
            return
        for manager in managers:
            _BackendSparklingNotebook.trigger_manager_draw(manager)
