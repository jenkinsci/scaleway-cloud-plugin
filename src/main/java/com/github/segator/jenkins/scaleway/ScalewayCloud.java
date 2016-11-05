/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 robert.gruendler@dubture.com
 *               2016 Maxim Biro <nurupo.contributions@gmail.com>
 *               2016 Isaac Aymerich <isaac.aymerich@gmail.com>
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
package com.github.segator.jenkins.scaleway;

import com.google.common.base.Strings;
import com.github.segator.scaleway.api.ScalewayClient;
import com.github.segator.scaleway.api.ScalewayFactory;
import com.github.segator.scaleway.api.entity.ScalewayServer;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * The {@link com.github.segator.jenkins.scaleway.ScalewayCloud} contains the
 * main configuration values for running slaves on Scaleway, e.g.
 * apiKey/clientId to connect to the API.
 *
 * The
 * {@link com.github.segator.jenkins.scaleway.ScalewayCloud#provision(hudson.model.Label, int)}
 * method will be called by Jenkins as soon as a new slave needs to be
 * provisioned.
 *
 *
 * @author isaac.aymerich@gmail.com
 */
public class ScalewayCloud extends hudson.slaves.Cloud {

    /**
     * The Scaleway API auth token
     *
     * @see "https://developers.scaleway.com/documentation/v2/#authentication"
     */
    private final String authToken;
    private final String orgToken;

    /**
     * The SSH private key associated with the selected SSH key
     */
    private final String privateKey;

    private final Integer instanceCap;

    private final Integer timeoutMinutes;

    /**
     * List of {@link com.github.segator.jenkins.scaleway.SlaveTemplate}
     */
    private final List<? extends SlaveTemplate> templates;

    private static final Logger LOGGER = Logger.getLogger(ScalewayCloud.class.getName());

    private final ScalewayClient scaleway;

    /**
     * Sometimes nodes can be provisioned very fast (or in parallel), leading to
     * more nodes being provisioned than the instance cap allows, as they all
     * check Scaleway at about the same time right before provisioning and see
     * that instance cap was not reached yet. So, for example, there might be a
     * situation where 2 nodes see that 1 more node can be provisioned before
     * the instance cap is reached, and they both happily provision, making one
     * more node being provisioned than the instance cap allows. Thus we need a
     * synchronization, so that only one node at a time could be provisioned, to
     * remove the race condition.
     */
    private static final Object provisionSynchronizor = new Object();

    /**
     * Constructor parameters are injected via jelly in the jenkins global
     * configuration
     *
     * @param name A name associated with this cloud configuration
     * @param authToken A Scaleway API authentication token, generated on their
     * website.
     * @param orgToken A Scaleway Organzation Token
     * @param privateKey An RSA private key in text format
     * @param instanceCap the maximum number of instances that can be started
     * @param timeoutMinutes timeout in minutes
     * @param templates the templates for this cloud
     */
    @DataBoundConstructor
    public ScalewayCloud(String name,
            String authToken,
            String orgToken,
            String privateKey,
            String instanceCap,
            String timeoutMinutes,
            List<? extends SlaveTemplate> templates) {
        super(name);

        LOGGER.log(Level.INFO, "Constructing new Cloud(name = {0}, <token>, <privateKey>, <keyId>, instanceCap = {1}, ...)", new Object[]{name, instanceCap});

        this.authToken = authToken;
        this.orgToken = orgToken;
        this.privateKey = privateKey;
        this.instanceCap = Integer.parseInt(instanceCap);
        this.timeoutMinutes = timeoutMinutes == null || timeoutMinutes.isEmpty() ? 5 : Integer.parseInt(timeoutMinutes);

        if (templates == null) {
            this.templates = Collections.emptyList();
        } else {
            this.templates = templates;
        }
        scaleway = ScalewayFactory.getScalewayClient(authToken, orgToken);

        LOGGER.info("Creating Scaleway cloud with " + this.templates.size() + " templates");
    }

    public boolean isInstanceCapReachedLocal() {
        if (instanceCap == 0) {
            return false;
        }

        int count = 0;

        LOGGER.log(Level.INFO, "cloud limit check");

        List<Node> nodes = new ArrayList();
        Jenkins instance = Jenkins.getInstance();
        if (instance != null) {
            nodes = instance.getNodes();
        }

        for (Node n : nodes) {
            if (ScalewayServerName.isServerInstanceOfCloud(n.getDisplayName(), name)) {
                count++;
            }
        }

        return count >= Math.min(instanceCap, getSlaveInstanceCap());
    }

    public boolean isInstanceCapReachedRemote(List<ScalewayServer> servers) {

        return false;
//        int count = 0;
//
//        LOGGER.log(Level.INFO, "cloud limit check");
//
//        for (ScalewayServer server : servers) {
//            if (server.isActive() || server.isNew()) {
//                if (ScalewayServerName.isServerInstanceOfCloud(server.getName(), name)) {
//                    count ++;
//                }
//            }
//        }
//
//        return count >= Math.min(instanceCap, getSlaveInstanceCap());
    }

    private int getSlaveInstanceCap() {
        int slaveTotalInstanceCap = 0;
        for (SlaveTemplate t : templates) {
            int slaveInstanceCap = t.getInstanceCap();
            if (slaveInstanceCap == 0) {
                slaveTotalInstanceCap = Integer.MAX_VALUE;
                break;
            } else {
                slaveTotalInstanceCap += t.getInstanceCap();
            }
        }

        return slaveTotalInstanceCap;
    }

    /**
     * The actual logic for provisioning a new server when it's needed by
     * Jenkins.
     *
     * @param label slave label
     * @param excessWorkload excess workload
     * @return List Planned Nodes
     */
    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(final Label label, int excessWorkload) {
        synchronized (provisionSynchronizor) {
            List<NodeProvisioner.PlannedNode> provisioningNodes = new ArrayList<NodeProvisioner.PlannedNode>();
            try {
                while (excessWorkload > 0) {

                    List<ScalewayServer> servers = scaleway.getAllServers();

                    if (isInstanceCapReachedLocal() || isInstanceCapReachedRemote(servers)) {
                        LOGGER.log(Level.INFO, "Instance cap reached, not provisioning.");
                        break;
                    }

                    final SlaveTemplate template = getTemplateBelowInstanceCap(servers, label);
                    if (template == null) {
                        break;
                    }

                    final String serverName = ScalewayServerName.generateServerName(name, template.getName());

                    provisioningNodes.add(new NodeProvisioner.PlannedNode(serverName, Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                        public Node call() throws Exception {
                            Slave slave;
                            synchronized (provisionSynchronizor) {
                                List<ScalewayServer> servers = scaleway.getAllServers();

                                if (isInstanceCapReachedLocal() || isInstanceCapReachedRemote(servers)) {
                                    LOGGER.log(Level.INFO, "Instance cap reached, not provisioning.");
                                    return null;
                                }
                                slave = template.provision(serverName, name, authToken, orgToken, privateKey, servers);
                            }
                            Jenkins instance = Jenkins.getInstance();
                            if (instance != null) {
                                instance.addNode(slave);
                            }
                            Computer slaveComputer = slave.toComputer();
                            if (slaveComputer != null) {
                                slaveComputer.connect(false).get();
                            }

                            return slave;
                        }
                    }), template.getNumExecutors()));

                    excessWorkload -= template.getNumExecutors();
                }

                LOGGER.info("Provisioning " + provisioningNodes.size() + " Scaleway nodes");

                return provisioningNodes;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, e.getMessage(), e);
                return Collections.emptyList();
            }
        }
    }

    @Override
    public boolean canProvision(Label label) {
        synchronized (provisionSynchronizor) {
            try {
                SlaveTemplate template = getTemplateBelowInstanceCapLocal(label);
                if (template == null) {
                    LOGGER.log(Level.INFO, "No slaves could provision for label " + label.getDisplayName() + " because they either didn't support such a label or have reached the instance cap.");
                    return false;
                }

                if (isInstanceCapReachedLocal()) {
                    LOGGER.log(Level.INFO, "Instance cap of " + getInstanceCap() + " reached, not provisioning for label " + label.getDisplayName() + ".");
                    return false;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, e.getMessage(), e);
            }

            return true;
        }
    }

    public List<SlaveTemplate> getTemplates(Label label) {
        List<SlaveTemplate> matchingTemplates = new ArrayList<SlaveTemplate>();

        for (SlaveTemplate t : templates) {
            if (label == null && !t.getLabelSet().isEmpty()) {
                continue;
            }
            if (t.getLabelSet() != null) {
                if ((label == null && t.getLabelSet().isEmpty()) || label!=null && label.matches(t.getLabelSet())) {
                    matchingTemplates.add(t);
                }
            }
        }

        return matchingTemplates;
    }

    public SlaveTemplate getTemplateBelowInstanceCap(List<ScalewayServer> servers, Label label) {
        List<SlaveTemplate> matchingTempaltes = getTemplates(label);

        try {
            for (SlaveTemplate t : matchingTempaltes) {
                if (!t.isInstanceCapReachedLocal(name) && !t.isInstanceCapReachedRemote(servers, name)) {
                    return t;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
        }

        return null;
    }

    public SlaveTemplate getTemplateBelowInstanceCapLocal(Label label) {
        List<SlaveTemplate> matchingTempaltes = getTemplates(label);

        try {
            for (SlaveTemplate t : matchingTempaltes) {
                if (!t.isInstanceCapReachedLocal(name)) {
                    return t;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
        }

        return null;
    }

    public String getName() {
        return name;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    public List<SlaveTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    public Integer getTimeoutMinutes() {
        return timeoutMinutes;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<hudson.slaves.Cloud> {

        public DescriptorImpl() {
            load();
        }

        public String getDisplayName() {
            return "Scaleway";
        }

        public FormValidation doTestConnection(@QueryParameter("authToken") final String authToken, @QueryParameter("orgToken") final String orgToken) {
            try {
                ScalewayClient client = ScalewayFactory.getScalewayClient(authToken, authToken);
                client.getAllOrganizations();
                return FormValidation.ok("Scaleway API request succeeded.");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to connect to Scaleway API", e);
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            if (Strings.isNullOrEmpty(name)) {
                return FormValidation.error("Must be set");
            } else if (!ScalewayServerName.isValidCloudName(name)) {
                return FormValidation.error("Must consist of A-Z, a-z, 0-9 and . symbols");
            } else {
                return FormValidation.ok();
            }
        }

        public static FormValidation doCheckAuthToken(@QueryParameter String authToken) {
            if (Strings.isNullOrEmpty(authToken)) {
                return FormValidation.error("Auth token must be set");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckPrivateKey(@QueryParameter String value) throws IOException {
            boolean hasStart = false, hasEnd = false;
            BufferedReader br = new BufferedReader(new StringReader(value));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals("-----BEGIN RSA PRIVATE KEY-----")) {
                    hasStart = true;
                }
                if (line.equals("-----END RSA PRIVATE KEY-----")) {
                    hasEnd = true;
                }
            }
            if (!hasStart) {
                return FormValidation.error("This doesn't look like a private key at all");
            }
            if (!hasEnd) {
                return FormValidation.error("The private key is missing the trailing 'END RSA PRIVATE KEY' marker. Copy&paste error?");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckInstanceCap(@QueryParameter String instanceCap) {
            if (Strings.isNullOrEmpty(instanceCap)) {
                return FormValidation.error("Instance cap must be set");
            } else {
                int instanceCapNumber;

                try {
                    instanceCapNumber = Integer.parseInt(instanceCap);
                } catch (Exception e) {
                    return FormValidation.error("Instance cap must be a number");
                }

                if (instanceCapNumber < 0) {
                    return FormValidation.error("Instance cap must be a positive number");
                }

                return FormValidation.ok();
            }
        }
    }

    public String getOrgToken() {
        return orgToken;
    }
}
