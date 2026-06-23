package worker;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

// AWT GUI for a worker: registration/connection state, capacity, active-job count, and a scrolling
// activity log fed by Worker's status hook. The worker runtime runs on its own thread.
public final class WorkerGUI extends Frame {

    private final Worker worker;
    private final String serverHost;
    private final int serverPort;

    private final Label stateValue = new Label("повезивање…");
    private final Label capacityValue = new Label("-");
    private final Label activeValue = new Label("0");
    private final TextArea logArea = new TextArea("", 14, 70,
        TextArea.SCROLLBARS_VERTICAL_ONLY);

    public WorkerGUI(Worker worker, String serverHost, int serverPort) {
        super("Радна станица — " + worker.getName());
        this.worker = worker;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        buildUi();
        worker.setStatusListener(this::appendLog);
        startStateRefresher();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        Panel info = new Panel(new GridLayout(0, 2, 6, 4));
        info.add(bold(new Label("Сервер:")));
        info.add(new Label(serverHost + ":" + serverPort));
        info.add(bold(new Label("Име станице:")));
        info.add(new Label(worker.getName()));
        info.add(bold(new Label("Стање:")));
        info.add(stateValue);
        info.add(bold(new Label("Капацитет (паралелни послови):")));
        info.add(capacityValue);
        info.add(bold(new Label("Активни послови:")));
        info.add(activeValue);
        add(info, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(logArea, BorderLayout.CENTER);

        Panel south = new Panel();
        Button clear = new Button("Очисти лог");
        clear.addActionListener(e -> logArea.setText(""));
        Button quit = new Button("Заустави станицу");
        quit.addActionListener(e -> shutdown());
        south.add(clear);
        south.add(quit);
        add(south, BorderLayout.SOUTH);

        setSize(720, 460);
        setLocationByPlatform(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    private static Label bold(Label l) {
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        return l;
    }

    private void appendLog(String line) {
        EventQueue.invokeLater(() -> logArea.append(line + "\n"));
    }

    // Polls the worker's live counters once a second (there is no push for these).
    private void startStateRefresher() {
        Thread t = new Thread(() -> {
            while (worker.isRunning()) {
                EventQueue.invokeLater(this::refreshState);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            EventQueue.invokeLater(this::refreshState);
        }, "worker-gui-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void refreshState() {
        boolean connected = worker.isConnected();
        stateValue.setText(connected ? "регистрована / повезана" : "није повезана");
        stateValue.setForeground(connected ? new Color(0, 128, 0) : Color.RED);
        capacityValue.setText(Integer.toString(worker.getCapacity()));
        activeValue.setText(Integer.toString(worker.getActiveJobs()));
    }

    private void shutdown() {
        worker.stop();
        dispose();
        System.exit(0);
    }

    public void showUi() {
        EventQueue.invokeLater(() -> setVisible(true));
    }
}
