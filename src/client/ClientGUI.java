package client;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

import common.JobInfo;
import common.JobSpec;
import common.JobType;
import common.Protocol;

// AWT GUI for the client: pick the components/connections files, choose the simulation type and end
// time, submit, and later look up status, fetch the result, or abort — keyed by job id from the
// on-disk TicketStore. Every network call runs on a background thread, so a slow/wrong server never
// freezes the UI.
public final class ClientGUI extends Frame {

    private final TextField hostField = new TextField("localhost", 12);
    private final TextField portField =
        new TextField(Integer.toString(Protocol.DEFAULT_SERVER_PORT), 6);
    private final TextField componentsField = new TextField("", 30);
    private final TextField connectionsField = new TextField("", 30);
    private final Choice typeChoice = new Choice();
    private final TextField endTimeField = new TextField("100", 8);
    private final TextField outputField = new TextField("rezultat.txt", 16);
    private final TextField jobIdField = new TextField("", 10);

    private final java.awt.List ticketList = new java.awt.List(8, false);
    private final TextArea logArea = new TextArea("", 12, 80,
        TextArea.SCROLLBARS_VERTICAL_ONLY);

    private final TicketStore tickets;

    public ClientGUI(TicketStore tickets) {
        super("Клијент — дистрибуирана симулација");
        this.tickets = tickets;
        for (JobType t : JobType.values()) {
            typeChoice.add(t.name());
        }
        buildUi();
        refreshTickets();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        add(buildForm(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        Panel logPanel = new Panel(new BorderLayout());
        logPanel.add(bold(new Label("Излаз / поруке")), BorderLayout.NORTH);
        logPanel.add(logArea, BorderLayout.CENTER);
        add(logPanel, BorderLayout.SOUTH);

        setSize(820, 640);
        setLocationByPlatform(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
                System.exit(0);
            }
        });
    }

    private Panel buildForm() {
        Panel form = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, c, row++, bold(new Label("Сервер (host:port):")), hostField, portField);

        c.gridx = 0; c.gridy = row; form.add(bold(new Label("Компоненте:")), c);
        c.gridx = 1; form.add(componentsField, c);
        c.gridx = 2; form.add(browseButton(componentsField), c);
        row++;

        c.gridx = 0; c.gridy = row; form.add(bold(new Label("Везе:")), c);
        c.gridx = 1; form.add(connectionsField, c);
        c.gridx = 2; form.add(browseButton(connectionsField), c);
        row++;

        addRow(form, c, row++, bold(new Label("Тип симулације:")), typeChoice, null);
        addRow(form, c, row++, bold(new Label("Крајње логичко време:")), endTimeField, null);
        addRow(form, c, row++, bold(new Label("Име излазног фајла:")), outputField, null);

        Button submit = new Button("Пошаљи посао");
        submit.addActionListener(e -> submit());
        c.gridx = 0; c.gridy = row; c.gridwidth = 3; form.add(submit, c);
        c.gridwidth = 1;
        return form;
    }

    private Panel buildCenter() {
        Panel center = new Panel(new BorderLayout(8, 8));

        Panel ticketsPanel = new Panel(new BorderLayout());
        ticketsPanel.add(bold(new Label("Локални тикети (jobId  излаз) — двоклик попуњава поље")),
            BorderLayout.NORTH);
        ticketList.addItemListener(e -> fillJobIdFromSelection());
        ticketsPanel.add(ticketList, BorderLayout.CENTER);
        center.add(ticketsPanel, BorderLayout.CENTER);

        Panel actions = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; actions.add(bold(new Label("Job ID:")), c);
        c.gridx = 1; actions.add(jobIdField, c);

        Button status = new Button("Статус");
        status.addActionListener(e -> status());
        Button result = new Button("Преузми резултат");
        result.addActionListener(e -> fetchResult());
        Button abort = new Button("Прекини посао");
        abort.addActionListener(e -> abort());
        Button refresh = new Button("Освежи тикете");
        refresh.addActionListener(e -> refreshTickets());

        c.gridx = 0; c.gridy = 1; actions.add(status, c);
        c.gridx = 1; actions.add(result, c);
        c.gridx = 0; c.gridy = 2; actions.add(abort, c);
        c.gridx = 1; actions.add(refresh, c);
        center.add(actions, BorderLayout.EAST);
        return center;
    }

    private static Label bold(Label l) {
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        return l;
    }

    private void addRow(Panel p, GridBagConstraints c, int row, Label label,
                        java.awt.Component f1, java.awt.Component f2) {
        c.gridx = 0; c.gridy = row; p.add(label, c);
        c.gridx = 1; p.add(f1, c);
        if (f2 != null) {
            c.gridx = 2; p.add(f2, c);
        }
    }

    private Button browseButton(TextField target) {
        Button b = new Button("Изабери…");
        b.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Изаберите фајл", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                target.setText(new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
            }
        });
        return b;
    }

    /* ----- actions (each runs the network call off the AWT thread) ----- */

    private void submit() {
        final String host = hostField.getText().trim();
        final int port;
        final long endTime;
        final JobType type;
        try {
            port = Integer.parseInt(portField.getText().trim());
            endTime = Long.parseLong(endTimeField.getText().trim());
            type = JobType.from(typeChoice.getSelectedItem());
        } catch (RuntimeException ex) {
            log("Грешка у параметрима: " + ex.getMessage());
            return;
        }
        final File comp = new File(componentsField.getText().trim());
        final File conn = new File(connectionsField.getText().trim());
        final String output = outputField.getText().trim();
        if (!comp.isFile() || !conn.isFile()) {
            log("Изаберите постојеће фајлове компоненти и веза.");
            return;
        }
        log("Шаљем посао на " + host + ":" + port + " …");
        runAsync(() -> {
            try {
                ClientSession session = new ClientSession(host, port);
                JobSpec spec = ClientSession.spec(type, endTime, output);
                String jobId = session.submit(spec, comp, conn);
                tickets.add(jobId, output);
                log("Послат посао " + jobId + " (излаз '" + output + "').");
                EventQueue.invokeLater(() -> {
                    jobIdField.setText(jobId);
                    refreshTickets();
                });
            } catch (IOException ex) {
                log("Слање није успело: " + ex.getMessage());
            }
        });
    }

    private void status() {
        final String jobId = jobIdField.getText().trim();
        if (jobId.isEmpty()) {
            log("Унесите/изаберите Job ID.");
            return;
        }
        final ClientSession session = session();
        if (session == null) {
            return;
        }
        runAsync(() -> {
            try {
                JobInfo info = session.status(jobId);
                log("Посао " + info.getJobId() + ": " + info.getStatus()
                    + msg(info.getMessage()));
            } catch (IOException ex) {
                log("Статус није доступан: " + ex.getMessage());
            }
        });
    }

    private void fetchResult() {
        final String jobId = jobIdField.getText().trim();
        if (jobId.isEmpty()) {
            log("Унесите/изаберите Job ID.");
            return;
        }
        final ClientSession session = session();
        if (session == null) {
            return;
        }
        String out = tickets.outputName(jobId);
        final String dest = out != null && !out.isEmpty() ? out : jobId + "-result.txt";
        runAsync(() -> {
            try {
                JobInfo info = session.fetchResult(jobId, new File(dest));
                if (info.hasResult()) {
                    log("Резултат сачуван у '" + dest + "' (" + info.getResultSize()
                        + " бајтова), статус " + info.getStatus() + ".");
                } else {
                    log("Резултат још није доступан (статус " + info.getStatus() + ")"
                        + msg(info.getMessage()));
                }
            } catch (IOException ex) {
                log("Преузимање није успело: " + ex.getMessage());
            }
        });
    }

    private void abort() {
        final String jobId = jobIdField.getText().trim();
        if (jobId.isEmpty()) {
            log("Унесите/изаберите Job ID.");
            return;
        }
        final ClientSession session = session();
        if (session == null) {
            return;
        }
        runAsync(() -> {
            try {
                boolean ok = session.abort(jobId);
                log("Прекид " + jobId + ": " + (ok ? "прихваћено" : "одбијено"));
            } catch (IOException ex) {
                log("Прекид није успео: " + ex.getMessage());
            }
        });
    }

    // Builds a session from the current host/port fields; logs a clear error on bad input.
    private ClientSession session() {
        try {
            return new ClientSession(hostField.getText().trim(),
                Integer.parseInt(portField.getText().trim()));
        } catch (NumberFormatException ex) {
            log("Неисправан порт: " + portField.getText());
            return null;
        }
    }

    private void fillJobIdFromSelection() {
        String sel = ticketList.getSelectedItem();
        if (sel != null) {
            int tab = sel.indexOf("  ");
            jobIdField.setText(tab > 0 ? sel.substring(0, tab) : sel.trim());
        }
    }

    private void refreshTickets() {
        ticketList.removeAll();
        for (String id : tickets.jobIds()) {
            String out = tickets.outputName(id);
            ticketList.add(id + "  " + (out == null ? "" : out));
        }
    }

    private static String msg(String m) {
        return m == null || m.isEmpty() ? "" : "  (" + m + ")";
    }

    private void log(String line) {
        EventQueue.invokeLater(() -> logArea.append(line + "\n"));
    }

    private void runAsync(Runnable task) {
        Thread t = new Thread(task, "client-action");
        t.setDaemon(true);
        t.start();
    }

    public void showUi() {
        EventQueue.invokeLater(() -> setVisible(true));
    }
}
