package rmi;

import conformance.rmi.TestInterface;

import java.io.FileNotFoundException;

public class RemoteClass implements TestInterface {

    @Override
    public Object method(boolean throw_exception) throws RMIException, FileNotFoundException {
        if(throw_exception == false) {
            return null;
        } else {
            throw new FileNotFoundException("");
        }
    }

    @Override
    public void rendezvous() throws RMIException {

    }
}
