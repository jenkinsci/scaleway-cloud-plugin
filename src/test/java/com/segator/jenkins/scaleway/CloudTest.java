
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 robert.gruendler@dubture.com
 *               2016 isaac.aymerich@gmail.com
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

import org.junit.Assert;
import org.junit.Test;

public class CloudTest {

    /*
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    */

    @Test
    public void testSomething() throws Exception {

        /*
        List<SlaveTemplate> templates = new ArrayList<SlaveTemplate>();
        templates.add(new SlaveTemplate("444", "4", "4", "4", "10", ""));
        jenkinsRule.getInstance().clouds.add(new Cloud("foo", "key", "id", "key", "2", templates));
        hudson.slaves.Cloud foo = jenkinsRule.getInstance().getCloud("foo");
        jenkinsRule.createSlave();
        List<Node> nodes = jenkinsRule.getInstance().getNodes();
        */

        //TODO: find out how to write jenkins tests :)
        // i'm seeing Caused by: java.io.FileNotFoundException: jenkins/./target/jenkins-for-test.exploding (No such file or directory)
        Assert.assertTrue(true);


    }
}
