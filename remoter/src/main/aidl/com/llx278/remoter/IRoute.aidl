// IRouter.aidl
package com.llx278.remoter;

// Declare any non-default types here with import statements
import com.llx278.remoter.IReceiver;
interface IRoute {
    void send(in byte[] data);
    void setReceiver(int from, in IReceiver receiver);
}
