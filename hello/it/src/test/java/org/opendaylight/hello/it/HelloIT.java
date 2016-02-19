/*
 * Copyright © 2015 left and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.hello.it;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.editConfigurationFilePut;

import com.google.common.util.concurrent.CheckedFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.mdsal.it.base.AbstractMdsalTestBase;
import org.opendaylight.hello.GreetingRegistryDataChangeListenerFuture;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hello.rev160218.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hello.rev160218.greeting.registry.GreetingRegistryEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hello.rev160218.greeting.registry.GreetingRegistryEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.hello.rev160218.greeting.registry.GreetingRegistryEntryKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption.LogLevel;
import org.ops4j.pax.exam.options.MavenUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class HelloIT extends AbstractMdsalTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(HelloIT.class);

    @Override
    public String getModuleName() {
        return "hello";
    }

    @Override
    public String getInstanceName() {
        return "hello-default";
    }

    @Override
    public MavenUrlReference getFeatureRepo() {
        return maven()
                .groupId("org.opendaylight.hello")
                .artifactId("hello-features")
                .classifier("features")
                .type("xml")
                .versionAsInProject();
    }

    @Override
    public String getFeatureName() {
        return "odl-hello-ui";
    }

    @Override
    public Option getLoggingOption() {
        Option option = editConfigurationFilePut(ORG_OPS4J_PAX_LOGGING_CFG,
                logConfiguration(HelloIT.class),
                LogLevel.INFO.name());
        option = composite(option, super.getLoggingOption());
        return option;
    }

    @Test
    public void testHelloFeatureLoad() {
        Assert.assertTrue(true);
    }

    @Test
    public void testRPC() throws TimeoutException, InterruptedException, ExecutionException {
        String name = "Sebastian";
        String response = "Hello " + name;
        DataBroker db = getSession().getSALService(DataBroker.class);
        GreetingRegistryDataChangeListenerFuture future =
                new GreetingRegistryDataChangeListenerFuture(db, name);
        validateGreetingRegistry(name, future);
        validateRPCResponse(name, response);
    }

    private void validateRPCResponse(String name, String response) throws InterruptedException, ExecutionException {
        HelloService service = getSession().getRpcService(HelloService.class);
        HelloWorldInput input = new HelloWorldInputBuilder()
                .setName(name)
                .build();
        Future<RpcResult<HelloWorldOutput>> outputFuture = service.helloWorld(input);
        RpcResult<HelloWorldOutput> outputResult = outputFuture.get();
        Assert.assertTrue("RPC was unsuccessful", outputResult.isSuccessful());
        Assert.assertEquals("Unexpected RPC response", response, outputResult.getResult().getGreeting());
    }

    private void validateGreetingRegistry(String name, GreetingRegistryDataChangeListenerFuture future) throws InterruptedException, TimeoutException, ExecutionException {
        future.get(100, TimeUnit.MILLISECONDS);
        Assert.assertTrue(name + " not recorded in greeting registry", future.isDone());
    }

    private void programResponse(String name, String response) throws TransactionCommitFailedException {
        DataBroker db = getSession().getSALService(DataBroker.class);
        WriteTransaction transaction = db.newWriteOnlyTransaction();
        InstanceIdentifier<GreetingRegistryEntry> iid = InstanceIdentifier.create(GreetingRegistry.class)
                .child(GreetingRegistryEntry.class, new GreetingRegistryEntryKey(name));
        GreetingRegistryEntry entry = new GreetingRegistryEntryBuilder()
                .setName(name)
                .setGreeting(response)
                .build();
        transaction.put(LogicalDatastoreType.CONFIGURATION, iid, entry);
        CheckedFuture<Void, TransactionCommitFailedException> future = transaction.submit();
        future.checkedGet();
    }

    @Test
    public void testProgrammableRPC() throws TimeoutException, InterruptedException, ExecutionException, TransactionCommitFailedException {
        String name = "Jesus";
        String response = "Hola " + name;
        programResponse(name, response);
        DataBroker db = getSession().getSALService(DataBroker.class);
        GreetingRegistryDataChangeListenerFuture future =
                new GreetingRegistryDataChangeListenerFuture(db, name);
        validateGreetingRegistry(name, future);
        validateRPCResponse(name, response);
    }
}
