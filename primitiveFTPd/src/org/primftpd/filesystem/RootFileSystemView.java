package org.primftpd.filesystem;

import org.primftpd.services.PftpdService;
import org.primftpd.pojo.LsOutputBean;
import org.primftpd.pojo.LsOutputParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public abstract class RootFileSystemView<T extends RootFile<X>, X> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Shell.Interactive shell;
    protected final PftpdService pftpdService;

    public RootFileSystemView(Shell.Interactive shell, PftpdService pftpdService) {
        this.shell = shell;
        this.pftpdService = pftpdService;
    }

    protected abstract T createFile(LsOutputBean bean, String absPath, PftpdService pftpdService);

    protected abstract String absolute(String file);

    public T getFile(String file) {
        logger.trace("getFile({})", file);

        String abs = absolute(file);
        logger.trace("  getFile(abs: {})", abs);

        final LsOutputParser parser = new LsOutputParser();
        final LsOutputBean[] wrapper = new LsOutputBean[1];
        final String cmd = "ls -lad " + RootFile.escapePath(abs);
        logger.trace("  running command: {}", cmd);
        shell.addCommand(cmd, 0, new Shell.OnCommandResultListener() {
            @Override
            public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                if (exitCode == 0) {
                    wrapper[0] = parser.parseLine(output.get(0));
                } else {
                    logger.error("could not run 'ls' command (exitCode: {})", exitCode);
                    if (output != null) {
                        for (String line : output) {
                            logger.error("{}", line);
                        }
                    }
                }
            }
        });
        shell.waitForIdle();
        LsOutputBean bean = wrapper[0];
        if (bean != null) {
            // don't deal with links on your own -> just causes errors, let the OS deal with it
            //if (bean.isLink()) {
            //    bean = findFinalLinkTarget(bean, parser);
            //    // TODO make sym link target absolute
            //    abs = bean.getName();
            //}
            return createFile(bean, abs, pftpdService);
        } else {
            // probably new
            String name;
            if (abs.contains("/")) {
                name = abs.substring(abs.lastIndexOf('/') + 1, abs.length());
            } else {
                name = abs;
            }
            bean = new LsOutputBean(name);
            return createFile(bean, abs, pftpdService);
        }
    }

    protected LsOutputBean findFinalLinkTarget(LsOutputBean bean, final LsOutputParser parser ) {
        LsOutputBean tmp = bean;
        final LsOutputBean[] wrapper = new LsOutputBean[1];
        int i=0;
        while (tmp.isLink()) {
            shell.addCommand("ls -lad \"" + tmp.getLinkTarget() + "\"", 0, new Shell.OnCommandLineListener() {
                @Override
                public void onSTDOUT(String s) {
                    wrapper[0] = parser.parseLine(s);
                }
                @Override
                public void onSTDERR(String s) {
                    logger.error("stderr: {}", s);
                }
                @Override
                public void onCommandResult(int i, int i1) {
                }
            });
            shell.waitForIdle();

            tmp = wrapper[0];
            i++;
            if (i > 20) {
                break;
            }
        }
        return tmp;
    }

}
