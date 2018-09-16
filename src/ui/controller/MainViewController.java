package ui.controller;

import ui.component.ControlledView;
import ui.component.Router;

public class MainViewController implements ControlledView {

    Router router;

    public void setScreenParent(Router viewParent) {
        router = viewParent;
    }
   
}
