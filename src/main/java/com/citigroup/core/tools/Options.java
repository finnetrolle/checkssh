package com.citigroup.core.tools;

import com.beust.jcommander.Parameter;

public class Options {

    @Parameter(names = {"--name", "-n"})
    String name;
    @Parameter(names = {"--remotes", "-r"})
    String remotesFilename;
    @Parameter(names = {"--jump-server", "-j"})
    String jumpServer;
    @Parameter(names = {"--password-file", "-pf"})
    String passwordFile;


}
