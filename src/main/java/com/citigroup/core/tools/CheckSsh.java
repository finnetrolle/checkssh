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
        hosts.forEach(host -> {
            System.out.println("\n\nTrying "+ host);
            AskResult result = askHost(session, host, options);
            System.out.println(result.toString());
        });
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
        config.put("PreferredAuthentications", "password");

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

    public static String readResponse(Channel channel, InputStream in) throws IOException {

        StringBuilder result = new StringBuilder("Result: ");
        byte[] tmp = new byte[1024];
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                String data = new String(tmp, 0, i);
                result.append(data);
                System.out.println(data);
            }
            if (channel.isClosed()) {
                System.out.println("Exit status: " + channel.getExitStatus());
                break;
            }
        }
        return result.toString();
    }

//    public static void execute(PrintStream printStream, String command) {
//        printStream.print(command + "\n");
//        printStream.flush();
//    }

//    public static AskResult askHost(Session session, String hostname, Options options) {
//        String command = String.format(SSH_COMMAND, options.name, hostname);
//        System.out.println("Command: " + command);
//        try {
//            Channel channel = session.openChannel("exec");
//            channel.connect();
//
//            ((ChannelExec) channel).setPty(true);
//            InputStream in = channel.getInputStream();
//            channel.setInputStream(null);
//            ((ChannelExec) channel).setErrStream(System.err);
//            PrintStream printStream = new PrintStream(channel.getOutputStream());
//
//            Thread.sleep(500);
//            execute(printStream, command);
//            Thread.sleep(500);
//            execute(printStream, options.password);
//
//            String response = readResponse(channel, in);
//            System.out.println("Response: [" + response + "]");
//
//            channel.disconnect();
//            return AskResult.success(hostname);
//        } catch (JSchException e) {
//            return AskResult.failed(hostname, e.getMessage());
//        } catch (IOException e) {
//            return AskResult.failed(hostname, e.getMessage());
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//            return AskResult.failed(hostname, e.getMessage());
//        }
//    }

//    public static AskResult askHost(Session session, String hostname, Options options) {
//        try {
//            Channel channel = session.openChannel("exec");
//            String command = String.format(SSH_COMMAND, options.name, hostname);
//            System.out.println("Executing: " + command);
//            ((ChannelExec) channel).setCommand(command);
//            channel.setInputStream(null);
//            OutputStream out = channel.getOutputStream();
//            ((ChannelExec) channel).setErrStream(System.err);
//            InputStream in = channel.getInputStream();
//            ((ChannelExec) channel).setPty(true);
//            channel.connect();
//            Thread.sleep(1000);
//            out.write((options.password + "\n").getBytes());
//            out.flush();
//            System.out.println("Read response");
//            String response = readResponse(channel, in);
//            System.out.println("Response: " + response);
//            channel.disconnect();
//            return AskResult.success(hostname);
//        } catch (JSchException e) {
//            return AskResult.failed(hostname, e.getMessage());
//        } catch (IOException e) {
//            return AskResult.failed(hostname, e.getMessage());
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//            return AskResult.failed(hostname, e.getMessage());
//        }
//    }

    public static AskResult askHost(Session session, String hostname, Options options) {
        try {
            ChannelFacade facade = ChannelFacade.open(session);
            String command = String.format(SSH_COMMAND, options.name, hostname);
            System.out.println(facade.execute(command));
            Thread.sleep(1000);
            System.out.println(facade.execute(options.password));
            facade.disconnect();
            return AskResult.success(hostname);
        } catch (IOException e) {
            return AskResult.failed(hostname, e.getMessage());
        } catch (InterruptedException e) {
            e.printStackTrace();
            return AskResult.failed(hostname, e.getMessage());
        }
    }

}
