package server;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

// AWT GUI for the central server: live job list with statuses, connected workers with capacities,
// and the server log; the abort button aborts the selected job. State is read on a background
// thread and pushed to the AWT thread via EventQueue.invokeLater.
public final class ServerGUI extends Frame {

    private final CentralServer server;
    private final int port;

    private final java.awt.List jobList = new java.awt.List(12, false);
    private final java.awt.List workerList = new java.awt.List(12, false);
    private final TextArea logArea = new TextArea("", 10, 100,
        TextArea.SCROLLBARS_VERTICAL_ONLY);

    // Job ids in the same order as the jobList rows, so a selection maps back to a job.
    private final List<String> jobIds = new ArrayList<>();

    public ServerGUI(CentralServer server, int port) {
        super("Централни сервер — порт " + port);
        this.server = server;
        this.port = port;
        buildUi();
        server.getLog().addListener(this::appendLog);
        startRefresher();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        Panel lists = new Panel(new GridLayout(1, 2, 8, 0));
        Panel jobsPanel = new Panel(new BorderLayout());
        jobsPanel.add(bold(new Label("Послови (id  статус  станица)")), BorderLayout.NORTH);
        jobsPanel.add(jobList, BorderLayout.CENTER);
        Panel workersPanel = new Panel(new BorderLayout());
        workersPanel.add(bold(new Label("Радне станице (име  активни/капацитет)")),
            BorderLayout.NORTH);
        workersPanel.add(workerList, BorderLayout.CENTER);
        lists.add(jobsPanel);
        lists.add(workersPanel);
        add(lists, BorderLayout.CENTER);

        Panel logPanel = new Panel(new BorderLayout());
        logPanel.add(bold(new Label("Лог сервера")), BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logPanel.add(logArea, BorderLayout.CENTER);
        add(logPanel, BorderLayout.SOUTH);

        Panel north = new Panel();
        Button abort = new Button("Прекини изабрани посао");
        abort.addActionListener(e -> abortSelected());
        Button refresh = new Button("Освежи");
        refresh.addActionListener(e -> refresh());
        north.add(abort);
        north.add(refresh);
        add(north, BorderLayout.NORTH);

        setSize(900, 600);
        setLocationByPlatform(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                server.stop();
                dispose();
                System.exit(0);
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

    private void abortSelected() {
        int idx = jobList.getSelectedIndex();
        if (idx < 0 || idx >= jobIds.size()) {
            return;
        }
        String id = jobIds.get(idx);
        ServerJob job = server.getJobs().get(id);
        if (job != null) {
            server.abortJob(job);
            refresh();
        }
    }

    private void startRefresher() {
        Thread t = new Thread(() -> {
            while (true) {
                EventQueue.invokeLater(this::refresh);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "server-gui-refresh");
        t.setDaemon(true);
        t.start();
    }

    private void refresh() {
        List<ServerJob> jobs = server.getJobs().all();
        int sel = jobList.getSelectedIndex();
        jobList.removeAll();
        jobIds.clear();
        for (ServerJob j : jobs) {
            jobIds.add(j.id);
            String worker = j.assignedWorker == null || j.assignedWorker.isEmpty()
                ? "-" : j.assignedWorker;
            String line = j.id + "  " + j.status + "  " + worker;
            if (j.message != null && !j.message.isEmpty()) {
                line += "  (" + j.message + ")";
            }
            if (j.finishedAt > 0) {
                line += "  " + (j.finishedAt - j.submittedAt) + " ms";
            }
            jobList.add(line);
        }
        if (sel >= 0 && sel < jobList.getItemCount()) {
            jobList.select(sel);
        }

        workerList.removeAll();
        for (WorkerHandle w : server.getWorkers().all()) {
            workerList.add(w.name + "  " + w.active + "/" + w.capacity
                + "  @" + w.peerHost + ":" + w.peerPort);
        }
    }

    public void showUi() {
        EventQueue.invokeLater(() -> setVisible(true));
    }
}
