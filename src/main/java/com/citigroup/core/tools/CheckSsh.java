package com.citigroup.core.tools;

import com.beust.jcommander.JCommander;
import com.jcraft.jsch.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.stream.Collectors;

public class CheckSsh {

    public static final String SSH_COMMAND = "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=3 %s@%s hostname";

    public static void main(String[] args) throws IOException {
        System.out.println("Parsing options...");
        Options options = new Options();
        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse(args);
        run(options);
    }

    public static void run(Options options) throws IOException {
        System.out.println("Loading password...");
        String password = Files.readAllLines(Paths.get(options.passwordFile)).get(0);
        System.out.println("Loading remotes...");
        List<String> hosts = loadHosts(options.remotesFilename);
        System.out.println("Connecting to jump box...");
        Session session = connectToJumpBox(options, password);
        System.out.println("Try each remote...");
        List<AskResult> results = hosts.stream()
                .map(host -> askHost(session, host, options, password))
                .collect(Collectors.toList());
        System.out.println("Complete. Saving to file...");
        save(results);
        session.disconnect();
        System.out.println("Done.");
    }

    public static void save(List<AskResult> results) {
        try {
            FileWriter writer = new FileWriter("out.txt");
            for (AskResult result : results) {
                writer.write(result.toString() + "\n");
            }
            writer.close();
        } catch (IOException e){
            System.out.println("Error while writing to file");
        }
    }

    public static List<String> loadHosts(String filename) {
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(filename));
        } catch (FileNotFoundException e) {
            System.out.println("File with hostnames not found. Exit.");
            System.exit(1);
        }
        List<String> result = new ArrayList<>();
        scanner.forEachRemaining(result::add);
        System.out.println("Loaded " + result.size() + " hostnames");
        return result;
    }

    public static Session connectToJumpBox(Options options, String password) {
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");

        JSch jSch = new JSch();
        Session session;
        try {
            session = jSch.getSession(options.name, options.jumpServer, 22);
            session.setPassword(password);
            session.setConfig(config);
            session.connect();
            System.out.println("Connected to Jump box " + options.jumpServer);
            return session;
        } catch (JSchException e) {
            System.out.println("Can't connect to jump box because of " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    public static String defineError(String result) {
        if (result.contains("Connection timed out")) {
            return "Connection timed out";
        }
        return "undefined error";
    }

    public static AskResult readResponse(Channel channel, InputStream in, String hostname) throws IOException {

        StringBuilder result = new StringBuilder();
        byte[] tmp = new byte[1024];
        long start = System.currentTimeMillis();
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                String data = new String(tmp, 0, i);
                result.append(data);
            }
            if (channel.isClosed()) {
                if (channel.getExitStatus() != 0) {
                    String error = defineError(result.toString());
                    return AskResult.failed(hostname, error);
                }
                break;
            }
            if (System.currentTimeMillis() > 5000 + start) {
                if (result.toString().contains("Permission denied, please try again.")) {
                    return AskResult.failed(hostname, "Permission denied");
                }
                if (result.toString().contains("Could not chdir to home directory")) {
                    return AskResult.success(hostname);
                }
                return AskResult.failed(hostname, "Connected, but met 5sec timeout");
            }
        }
        if (result.toString().contains(hostname)) {
            return AskResult.success(hostname);
        }
        return AskResult.failed(hostname, "Unknown reason");
    }

    public static AskResult askHost(Session session, String hostname, Options options, String password) {
        System.out.println("Trying to ask " + hostname);
        try {
            Channel channel = session.openChannel("exec");
            String command = String.format(SSH_COMMAND, options.name, hostname);
            ((ChannelExec) channel).setCommand(command);
            channel.setInputStream(null);
            OutputStream out = channel.getOutputStream();
            ((ChannelExec) channel).setErrStream(System.err);
            InputStream in = channel.getInputStream();
            ((ChannelExec) channel).setPty(true);
            channel.connect();
            Thread.sleep(1000);
            out.write((password + "\n").getBytes());
            out.flush();
            AskResult response = readResponse(channel, in, hostname);
            channel.disconnect();
            return response;
        } catch (JSchException e) {
            return AskResult.failed(hostname, "Jsch exception:" + e.getMessage());
        } catch (IOException e) {
            return AskResult.failed(hostname, "IOException: " + e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
            return AskResult.failed(hostname, e.getMessage());
        }
    }

}
