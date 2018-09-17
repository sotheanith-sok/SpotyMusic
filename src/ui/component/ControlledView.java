package ui.component;

public interface ControlledView {

    //inject Parent ViewPane
    void setViewParent(Router viewPage);

    //lifecycle functions
    //oncreate
    //onready
    //onshow
    //pass information for new screens to load if they need more resources
}
