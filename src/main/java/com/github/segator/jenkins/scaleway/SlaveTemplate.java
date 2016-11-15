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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Strings;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProperty;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.github.segator.scaleway.api.entity.ScalewayServer;
import com.github.segator.scaleway.api.ScalewayClient;
import com.github.segator.scaleway.api.ScalewayFactory;
import com.github.segator.scaleway.api.constants.ScalewayComputeRegion;
import com.github.segator.scaleway.api.entity.ScalewayCommercialType;
import com.github.segator.scaleway.api.entity.ScalewayImage;
import com.github.segator.scaleway.api.entity.ScalewayServerAction;
import com.github.segator.scaleway.api.entity.ScalewayServerDefinition;
import com.github.segator.scaleway.api.entity.ScalewayState;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A {@link SlaveTemplate} represents the configuration values for creating a
 * new slave via a Scaleway.
 *
 * <p>
 * Holds things like Image ID, sizeId used for the specific Server.
 *
 * <p>
 * The SlaveTemplate method is the main entry point to create a new server via
 * the Scaleway API when a new slave needs to be provisioned.
 *
 * @author robert.gruendler@dubture.com
 * @author isaac.aymerich@gmail.com
 */
@SuppressWarnings("unused")
public class SlaveTemplate implements Describable<SlaveTemplate> {

    private final String name;

    private final String labelString;

    private final int idleTerminationInMinutes;

    /**
     * The maximum number of executors that this slave will run.
     */
    private final int numExecutors;

    private final String labels;

    /**
     * The Image to be used for the server.
     */
    private final String imageId;

    /**
     * The specified server sizeId.
     */
    private final String sizeId;

    private final String username;

    private final String workspacePath;

    private final Integer sshPort;

    private final Integer instanceCap;

    /**
     * Setup script for preparing the new slave. Differs from userData in that
     * Jenkins runs this script, as opposed to the Scaleway provisioning
     * process.
     */
    private final String initScript;

    private transient Set<LabelAtom> labelSet;

    private static final Logger LOGGER = Logger.getLogger(SlaveTemplate.class.getName());

    /**
     * Data is injected from the global Jenkins configuration via jelly.
     *
     * @param imageId an image slug e.g. "debian-8-x64", or an integer e.g. of a
     * backup, such as "12345678"
     * @param sizeId the image size e.g. "512mb" or "1gb"
     * @param idleTerminationInMinutes how long to wait before destroying a
     * slave
     * @param numExecutors the number of executors that this slave supports
     * @param labelString the label for this slave
     * @param initScript setup script to configure the slave
     */
    @DataBoundConstructor
    public SlaveTemplate(String name, String imageId, String sizeId, String username, String workspacePath,
            Integer sshPort, String idleTerminationInMinutes, String numExecutors, String labelString,
            String instanceCap, String initScript) {

        LOGGER.log(Level.INFO, "Creating SlaveTemplate with imageId = {0}, sizeId = {1}",
                new Object[]{imageId, sizeId});

        this.name = name;
        this.imageId = imageId;
        this.sizeId = sizeId;
        this.username = username;
        this.workspacePath = workspacePath;
        this.sshPort = sshPort;

        this.idleTerminationInMinutes = tryParseInteger(idleTerminationInMinutes, 10);
        this.numExecutors = tryParseInteger(numExecutors, 1);
        this.labelString = labelString;
        this.labels = Util.fixNull(labelString);
        this.instanceCap = Integer.parseInt(instanceCap);
        this.initScript = initScript;

        readResolve();
    }

    public boolean isInstanceCapReachedLocal(String cloudName) {
          Jenkins instance = Jenkins.getInstance();
        
        if (instanceCap == 0) {
            return false;
        }
        LOGGER.log(Level.INFO, "slave limit check");

        int count = 0;
        List<Node> nodes = new ArrayList();
        if (instance != null){
            nodes = instance.getNodes();
        }
        for (Node n : nodes) {
            if (ScalewayServerName.isServerInstanceOfSlave(n.getDisplayName(), cloudName, name)) {
                count++;
            }
        }

        return count >= instanceCap;
    }

    public boolean isInstanceCapReachedRemote(List<ScalewayServer> servers, String cloudName) {
        LOGGER.log(Level.INFO, "slave limit check");
        int count = 0;
        for (ScalewayServer server : servers) {
            if ((server.getState() == ScalewayState.RUNNING)) {
                if (ScalewayServerName.isServerInstanceOfSlave(server.getName(), cloudName, name)) {
                    count++;
                }
            }
        }

        return count >= instanceCap;
    }

    public Slave provision(String serverName, String cloudName,String orgToken,ScalewayClient scaleway, String privateKey, List<ScalewayServer> servers)
            throws IOException, Descriptor.FormException {

        LOGGER.log(Level.INFO, "Provisioning slave...");

        try {
            LOGGER.log(Level.INFO, "Starting to provision Scaleway Server using image: " + imageId + ", sizeId: " + sizeId);

            if (isInstanceCapReachedLocal(cloudName) || isInstanceCapReachedRemote(servers, cloudName)) {
                throw new AssertionError();
            }

            // create a new server
            ScalewayServerDefinition serverDefinition = new ScalewayServerDefinition();
            serverDefinition.setName(serverName);
            serverDefinition.setImage(imageId);
            serverDefinition.setOrganization(orgToken);
            serverDefinition.setDynamicIpRequired(true);
            serverDefinition.setTags(Arrays.asList("jenkins-slave"));
            serverDefinition.setCommercialType(ScalewayCommercialType.C2S);

            LOGGER.log(Level.INFO, "Creating slave with new server " + serverName);

            ScalewayServer createdServer = scaleway.createServer(serverDefinition);
            scaleway.executeServerAction(createdServer, ScalewayServerAction.POWER_ON);
            return newSlave(cloudName, createdServer, privateKey);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e.getMessage(), e);
            throw new AssertionError();
        }
    }

    /**
     * Create a new {@link Slave} from the given {@link Server}
     *
     * @param server the server being created
     * @param privateKey the RSA private key being used
     * @return the provisioned {@link Slave}
     * @throws IOException
     * @throws Descriptor.FormException
     */
    private Slave newSlave(String cloudName, ScalewayServer server, String privateKey) throws IOException, Descriptor.FormException {
        LOGGER.log(Level.INFO, "Creating new slave...");
        return new Slave(
                cloudName,
                server.getName(),
                "Computer running on Scaleway with name: " + server.getName(),
                server.getId(),
                privateKey,
                username,
                workspacePath,
                sshPort,
                numExecutors,
                idleTerminationInMinutes,
                Node.Mode.NORMAL,
                labels,
                new ScalewayComputerLauncher(),
                new RetentionStrategy(),
                Collections.<NodeProperty<?>>emptyList(),
                Util.fixNull(initScript),
                ""
        );
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<SlaveTemplate> {

        @Override
        public String getDisplayName() {
            return "Slave Template";
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            if (Strings.isNullOrEmpty(name)) {
                return FormValidation.error("Must be set");
            } else if (!ScalewayServerName.isValidSlaveName(name)) {
                return FormValidation.error("Must consist of A-Z, a-z, 0-9 and . symbols");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckUsername(@QueryParameter String username) {
            if (Strings.isNullOrEmpty(username)) {
                return FormValidation.error("Must be set");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckWorkspacePath(@QueryParameter String workspacePath) {
            if (Strings.isNullOrEmpty(workspacePath)) {
                return FormValidation.error("Must be set");
            } else {
                return FormValidation.ok();
            }
        }

        private static FormValidation doCheckNonNegativeNumber(String stringNumber) {
            if (Strings.isNullOrEmpty(stringNumber)) {
                return FormValidation.error("Must be set");
            } else {
                int number;

                try {
                    number = Integer.parseInt(stringNumber);
                } catch (Exception e) {
                    return FormValidation.error("Must be a number");
                }

                if (number < 0) {
                    return FormValidation.error("Must be a nonnegative number");
                }

                return FormValidation.ok();
            }
        }

        public FormValidation doCheckSshPort(@QueryParameter String sshPort) {
            return doCheckNonNegativeNumber(sshPort);
        }

        public FormValidation doCheckNumExecutors(@QueryParameter String numExecutors) {
            if (Strings.isNullOrEmpty(numExecutors)) {
                return FormValidation.error("Must be set");
            } else {
                int number;

                try {
                    number = Integer.parseInt(numExecutors);
                } catch (Exception e) {
                    return FormValidation.error("Must be a number");
                }

                if (number <= 0) {
                    return FormValidation.error("Must be a positive number");
                }

                return FormValidation.ok();
            }
        }

        public FormValidation doCheckIdleTerminationInMinutes(@QueryParameter String idleTerminationInMinutes) {
            if (Strings.isNullOrEmpty(idleTerminationInMinutes)) {
                return FormValidation.error("Must be set");
            } else {

                try {
                    Integer.parseInt(idleTerminationInMinutes);
                } catch (Exception e) {
                    return FormValidation.error("Must be a number");
                }

                return FormValidation.ok();
            }
        }

        public FormValidation doCheckInstanceCap(@QueryParameter String instanceCap) {
            return doCheckNonNegativeNumber(instanceCap);
        }

        public FormValidation doCheckSizeId(@RelativePath("..") @QueryParameter String authToken) {
            return ScalewayCloud.DescriptorImpl.doCheckAuthToken(authToken);
        }

        public FormValidation doCheckImageId(@RelativePath("..") @QueryParameter String authToken) {
            return ScalewayCloud.DescriptorImpl.doCheckAuthToken(authToken);
        }

        public FormValidation doCheckRegionId(@RelativePath("..") @QueryParameter String authToken) {
            return ScalewayCloud.DescriptorImpl.doCheckAuthToken(authToken);
        }

        public ListBoxModel doFillSizeIdItems() throws Exception {

            ListBoxModel model = new ListBoxModel();

            for (ScalewayCommercialType size : ScalewayCommercialType.values()) {
                model.add(size.toString(), size.toString());
            }

            return model;
        }

        public ListBoxModel doFillImageIdItems(@RelativePath("..") @QueryParameter String authToken, @RelativePath("..") @QueryParameter String orgToken,@RelativePath("..") @QueryParameter String regionId) throws Exception {

            ScalewayClient scaleway = ScalewayFactory.getScalewayClient(authToken, orgToken,ScalewayComputeRegion.valueOf(regionId));
            ListBoxModel model = new ListBoxModel();
            List<ScalewayImage> images = scaleway.getAllImages();
            for (ScalewayImage image : images) {
                model.add(image.getName() + "(" + image.getArch() + ")", image.getId());
            }

            return model;
        }

    }

    @SuppressWarnings("unchecked")
    public Descriptor<SlaveTemplate> getDescriptor() {
        Jenkins instance = Jenkins.getInstance();
        if (instance != null) {
            return instance.getDescriptor(getClass());
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getSizeId() {
        return sizeId;
    }

    public String getLabels() {
        return labels;
    }

    public String getLabelString() {
        return labelString;
    }

    public Set<LabelAtom> getLabelSet() {
        return labelSet;
    }

    public String getImageId() {
        return imageId;
    }

    public String getUsername() {
        return username;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    public int getIdleTerminationInMinutes() {
        return idleTerminationInMinutes;
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    public String getInitScript() {
        return initScript;
    }

    public int getSshPort() {
        return sshPort;
    }

    private static int tryParseInteger(final String integerString, final int defaultValue) {
        try {
            return Integer.parseInt(integerString);
        } catch (NumberFormatException e) {
            LOGGER.log(Level.INFO, "Invalid integer {0}, defaulting to {1}", new Object[]{integerString, defaultValue});
            return defaultValue;
        }
    }

    protected Object readResolve() {
        labelSet = Label.parse(labels);
        return this;
    }
}
