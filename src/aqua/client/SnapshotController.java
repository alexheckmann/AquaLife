package aqua.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SnapshotController implements ActionListener {

    private final Component parent;
    private final TankModel tankModel;
    private final TankView tankView;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    public SnapshotController(Component component, TankModel tankModel, TankView tankView) {

        parent = component;
        this.tankModel = tankModel;
        this.tankView = tankView;
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
