// IReceiver.aidl
package com.llx278.remoter;

// Declare any non-default types here with import statements

interface IReceiver {
    void onReceive(in byte[] data);
}
