package com.citigroup.core.tools;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class ChannelFacade {

    private final Channel channel;
    private final InputStream in;
    private final PrintStream printStream;

    public ChannelFacade(Channel channel, InputStream in, PrintStream printStream) {
        this.channel = channel;
        this.in = in;
        this.printStream = printStream;
    }

    public void disconnect() {
        this.channel.disconnect();
    }

    public String execute(String command) throws IOException {
        printStream.print(command + "\n");
        printStream.flush();
        StringBuilder result = new StringBuilder();
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

    public static ChannelFacade open(Session session) {
        try {
            Channel channel = session.openChannel("exec");
            channel.connect();
            InputStream in = channel.getInputStream();
            channel.setInputStream(null);
            ((ChannelExec) channel).setErrStream(System.err);
            PrintStream printStream = new PrintStream(channel.getOutputStream());
            ((ChannelExec) channel).setPty(true);

            return new ChannelFacade(channel, in, printStream);
        } catch (JSchException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot open channel", e);
        }
    }

}
