/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 robert.gruendler@dubture.com
 *               2016 Maxim Biro <nurupo.contributions@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.segator.jenkins.scaleway;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * The {@link com.segator.jenkins.scaleway.Slave} is responsible for
 *
 * <ul>
 *   <li>Creating a Scaleway {@link com.segator.jenkins.scaleway.Computer}</li>
 * </ul>
 *
 * @author robert.gruendler@segator.com
 */
public class Slave extends AbstractCloudSlave {

    private static final Logger LOG = Logger.getLogger(Slave.class.getName());

    private final String cloudName;

    private final int idleTerminationTime;

    private final String initScript;

    private final String serverId;

    private final String privateKey;

    private final String remoteAdmin;

    private final String jvmOpts;

    private final long startTimeMillis;

    private final int sshPort;

    /**
     * {@link Slave}s are created by {@link SlaveTemplate}s
     */
    public Slave(String cloudName, String name, String nodeDescription, String serverId, String privateKey,
                 String remoteAdmin, String remoteFS, int sshPort, int numExecutors, int idleTerminationTime,
                 Mode mode, String labelString, ComputerLauncher launcher, RetentionStrategy retentionStrategy,
                 List<? extends NodeProperty<?>> nodeProperties, String initScript, String jvmOpts)
            throws Descriptor.FormException, IOException {

        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);

        this.cloudName = cloudName;
        this.serverId = serverId;
        this.privateKey = privateKey;
        this.remoteAdmin = remoteAdmin;
        this.idleTerminationTime = idleTerminationTime;
        this.initScript = initScript;
        this.jvmOpts = jvmOpts;
        this.sshPort = sshPort;
        startTimeMillis = System.currentTimeMillis();
    }

    @Extension
    public static class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Scaleway Slave";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }

    /**
     * Override to create a Scaleway {@link com.segator.jenkins.scaleway.Computer}
     * @return a new Computer instance, instantiated with this Slave instance.
     */
    @Override
    public Computer createComputer() {
        return new Computer(this);
    }

    /**
     * Retrieve a handle to the associated {@link com.segator.jenkins.scaleway.Cloud}
     * @return the Cloud associated with the specified cloudName
     */
    public Cloud getCloud() {
        return (Cloud) Jenkins.getInstance().getCloud(cloudName);
    }

    /**
     * Get the name of the remote admin user
     * @return the remote admin user, defaulting to "root"
     */
    public String getRemoteAdmin() {
        if (remoteAdmin == null || remoteAdmin.length() == 0)
            return "root";
        return remoteAdmin;
    }

    /**
     * Deletes the Scaleway Server when not needed anymore.
     *
     * @param listener Unused
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        Scaleway.tryDestroyServerAsync(getCloud().getAuthToken(),getCloud().getOrgToken(), serverId);
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public String getServerId() {
        return serverId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public int getIdleTerminationTime() {
        return idleTerminationTime;
    }

    public String getInitScript() {
        return initScript;
    }

    public String getJvmOpts() {
        return jvmOpts;
    }

    public int getSshPort() {
        return sshPort;
    }
}
