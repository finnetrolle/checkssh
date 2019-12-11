package com.citigroup.core.tools;

import com.beust.jcommander.JCommander;
import com.jcraft.jsch.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class CheckSsh {

    public static final String SSH_COMMAND = "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=3 %s@%s hostname";

    public static void main(String[] args) {
        Options options = new Options();
        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse(args);
        run(options);
    }

    public static void run(Options options) {
        Session session = connectToJumpBox(options);
        List<String> hosts = loadHosts(options.remotesFilename);
        hosts.forEach(host -> System.out.println(askHost(session, host, options)));
        session.disconnect();
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
        return result;
    }

    public static Session connectToJumpBox(Options options) {
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");

        JSch jSch = new JSch();
        Session session;
        try {
            session = jSch.getSession(options.name, options.jumpServer, 22);
            session.setPassword(options.password);
            session.setConfig(config);
            session.connect();
            System.out.println("Connected to Jump box " + options.jumpServer);
            return session;
        } catch (JSchException e) {
            if ("Auth fail".equals(e.getMessage())) {
                System.out.println("Can't connect to Jump box");
            }
            System.exit(1);
            return null;
        }
    }

    public static AskResult readResponse(Channel channel, InputStream in, String hostname) throws IOException {

        StringBuilder result = new StringBuilder();
        byte[] tmp = new byte[1024];
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                String data = new String(tmp, 0, i);
                result.append(data);
            }
            if (channel.isClosed()) {
                if (channel.getExitStatus() != 0) {
                    return AskResult.failed(hostname, "SSH execution result is not 0 but " + channel.getExitStatus());
                }
                break;
            }
        }
        if (result.toString().contains(hostname)) {
            return AskResult.success(hostname);
        }
        return AskResult.failed(hostname, "Unknown reason");
    }

    public static AskResult askHost(Session session, String hostname, Options options) {
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
            out.write((options.password + "\n").getBytes());
            out.flush();
            AskResult response = readResponse(channel, in, hostname);
            channel.disconnect();
            return response;
        } catch (JSchException e) {
            return AskResult.failed(hostname, e.getMessage());
        } catch (IOException e) {
            return AskResult.failed(hostname, e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
            return AskResult.failed(hostname, e.getMessage());
        }
    }

}
