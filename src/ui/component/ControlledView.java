package ui.component;

public interface ControlledView {

   //inject Parent ViewPane
   void setViewParent(Router viewPage);

   default void beforeShow() {
   }

   //lifecycle functions
   //oncreate
   //onready
   //onshow
   //pass information for new screens to load if they need more resources
}
