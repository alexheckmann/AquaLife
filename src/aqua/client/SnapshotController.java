package aqua.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SnapshotController implements ActionListener {

    private final Component parent;
    private final TankModel tankModel;

    public SnapshotController(Component component, TankModel tankModel) {

        parent = component;
        this.tankModel = tankModel;
    }


    /**
     * Invoked when an action occurs.
     *
     * @param e the event to be processed
     */
    @Override
    public void actionPerformed(ActionEvent e) {

        JOptionPane.showMessageDialog(parent, "Initiate a global snapshot");
        tankModel.initiateSnapshot();
    }

}
