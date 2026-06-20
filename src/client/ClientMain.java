package rs.ac.bg.etf.kdp.client;

import java.io.File;
import java.io.IOException;

import rs.ac.bg.etf.kdp.common.JobInfo;
import rs.ac.bg.etf.kdp.common.JobSpec;
import rs.ac.bg.etf.kdp.common.JobType;

/**
 * Console client (the Swing GUI is added later). Argument-driven so the seven test scenarios
 * can be scripted.
 *
 * <pre>
 * java ... ClientMain &lt;host&gt; &lt;port&gt; submit &lt;components&gt; &lt;connections&gt; &lt;type&gt; &lt;endTime&gt; &lt;outputName&gt;
 * java ... ClientMain &lt;host&gt; &lt;port&gt; status &lt;jobId&gt;
 * java ... ClientMain &lt;host&gt; &lt;port&gt; result &lt;jobId&gt; [destFile]
 * java ... ClientMain &lt;host&gt; &lt;port&gt; abort  &lt;jobId&gt;
 * java ... ClientMain &lt;host&gt; &lt;port&gt; list
 * </pre>
 */
public class ClientMain {

    public static void main(String[] args) {
        if (args.length < 3) {
            usage();
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String command = args[2];

        ClientSession session = new ClientSession(host, port);
        TicketStore tickets = new TicketStore("tickets/tickets.txt");

        try {
            switch (command) {
                case "submit":
                    doSubmit(session, tickets, args);
                    break;
                case "status":
                    doStatus(session, args[3]);
                    break;
                case "result":
                    doResult(session, tickets, args);
                    break;
                case "abort":
                    System.out.println("abort " + args[3] + ": "
                        + (session.abort(args[3]) ? "OK" : "rejected"));
                    break;
                case "list":
                    doList(session, tickets);
                    break;
                default:
                    usage();
            }
        } catch (IOException e) {
            // Covers Test 5 (foreign/web server): clean message instead of hanging.
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void doSubmit(ClientSession session, TicketStore tickets, String[] args)
            throws IOException {
        if (args.length < 8) {
            usage();
            return;
        }
        String components = args[3];
        String connections = args[4];
        JobType type = JobType.from(args[5]);
        long endTime = Long.parseLong(args[6]);
        String outputName = args[7];

        JobSpec spec = ClientSession.buildSpec(components, connections, type, endTime, outputName);
        String jobId = session.submit(spec);
        tickets.add(jobId, outputName);
        System.out.println("Submitted job " + jobId + " (output '" + outputName + "').");
    }

    private static void doStatus(ClientSession session, String jobId) throws IOException {
        printInfo(session.status(jobId));
    }

    private static void doResult(ClientSession session, TicketStore tickets, String[] args)
            throws IOException {
        String jobId = args[3];
        String outputName = tickets.outputName(jobId);
        String dest = args.length >= 5 ? args[4]
            : (outputName != null && !outputName.isEmpty() ? outputName : jobId + "-result.txt");
        JobInfo info = session.fetchResult(jobId, new File(dest));
        printInfo(info);
        if (info.hasResult()) {
            System.out.println("Result saved to '" + dest + "' (" + info.getResultSize() + " bytes).");
        } else {
            System.out.println("Result not available yet (status " + info.getStatus() + ").");
        }
    }

    private static void doList(ClientSession session, TicketStore tickets) {
        if (tickets.jobIds().isEmpty()) {
            System.out.println("No local tickets.");
            return;
        }
        for (String jobId : tickets.jobIds()) {
            try {
                JobInfo info = session.status(jobId);
                System.out.println(jobId + "  " + info.getStatus()
                    + (info.getMessage() == null || info.getMessage().isEmpty()
                        ? "" : "  (" + info.getMessage() + ")"));
            } catch (IOException e) {
                System.out.println(jobId + "  <unreachable: " + e.getMessage() + ">");
            }
        }
    }

    private static void printInfo(JobInfo info) {
        System.out.println("Job " + info.getJobId() + ": " + info.getStatus()
            + (info.getMessage() == null || info.getMessage().isEmpty()
                ? "" : "  (" + info.getMessage() + ")"));
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  ClientMain <host> <port> submit <components> <connections> "
            + "<type> <endTime> <outputName>");
        System.out.println("  ClientMain <host> <port> status <jobId>");
        System.out.println("  ClientMain <host> <port> result <jobId> [destFile]");
        System.out.println("  ClientMain <host> <port> abort  <jobId>");
        System.out.println("  ClientMain <host> <port> list");
        System.out.println("  <type> = SINGLETHREAD | MULTITHREAD | OPTIMISTIC");
    }
}
