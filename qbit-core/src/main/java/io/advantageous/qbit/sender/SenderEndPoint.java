package io.advantageous.qbit.sender;

import io.advantageous.qbit.message.Message;
import io.advantageous.qbit.message.MethodCall;
import io.advantageous.qbit.service.BeforeMethodCall;
import io.advantageous.qbit.service.EndPoint;
import io.advantageous.qbit.service.impl.NoOpBeforeMethodCall;
import io.advantageous.qbit.spi.ProtocolEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Combines a sender with a protocol encoder so we can send messages to another remote end point.
 * Created by Richard on 10/1/14.
 *  @author Rick Hightower
 */
public class SenderEndPoint implements EndPoint {


    final ProtocolEncoder encoder;
    final String address;
    private final Sender<String> sender;
    private final BeforeMethodCall beforeMethodCall;
    private final BlockingQueue<MethodCall<Object>> methodCalls;

    private final int requestBatchSize;

    public SenderEndPoint(ProtocolEncoder encoder, String address, Sender<String> sender, BeforeMethodCall beforeMethodCall,
                         int requestBatchSize ) {
        this.encoder = encoder;
        this.address = address;

        this.beforeMethodCall = beforeMethodCall == null ? new NoOpBeforeMethodCall() : beforeMethodCall;

        this.requestBatchSize = requestBatchSize;
        this.methodCalls = new ArrayBlockingQueue<>(requestBatchSize);

        this.sender = sender;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public void call(MethodCall<Object> methodCall) {

        beforeMethodCall.before(methodCall);

        if (!methodCalls.offer(methodCall)) {
            flush(methodCall);
        }

    }

    @Override
    public void call(List<MethodCall<Object>> methodCalls) {

        if (methodCalls.size()>0) {
            String returnAddress = methodCalls.get(0).returnAddress();
            List<Message<Object>> methods =  (List<Message<Object>>) (Object) methodCalls;
            sender.send(returnAddress, encoder.encodeAsString(methods));
        }
    }


    @Override
    public void flush() {

        flush(null);
    }

    private synchronized void flush(MethodCall<Object> lastMethodCall) {


        Message<Object> method = methodCalls.poll();

        if (method == null) {
            return;
        }


        List<Message<Object>> methods = null;

        String returnAddress = ((MethodCall<Object>)method).returnAddress();

        methods = new ArrayList<>(requestBatchSize+1);

        while (method != null) {
            methods.add(method);
            method = methodCalls.poll();

        }

        if (lastMethodCall!=null) {
            methods.add(lastMethodCall);
        }


        sender.send(returnAddress, encoder.encodeAsString(methods));


    }
}