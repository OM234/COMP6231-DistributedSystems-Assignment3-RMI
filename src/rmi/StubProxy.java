package rmi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;


/**
 * InvocationHandler for stubs. Connects the stubs to their associated skeletons.
 * Marshals arguments, unmarshals responses
 */
public class StubProxy implements InvocationHandler {

    /**
     * Object which calls the method
     */
    Object target;
    /**
     * Skeleton which services the method call
     */
    Skeleton skeleton;

    public StubProxy(){
        target = null;
        skeleton = null;
    }

    public StubProxy(Object object, Skeleton skeleton){
        target = object;
        this.skeleton = skeleton;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        //The invocation handler can deal with .equals(), .hashCode(), and .toString() by itself
        if(method.getName().equals("equals")) {
            return isEqual(args);
        }
        if(method.getName().equals("hashCode")) {
            return Objects.hash(skeleton);
        }
        if(method.getName().equals("toString")) {
            return (this.toString());
        }
        if (skeleton == null || target == null) {
            throw new RMIException("No skeleton or target");
        }

        //Invoke the method. Pass along any exceptions
        try {
            return method.invoke(target, args);
        }
        catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    /**
     * Determines if 2 objects are the same skeleton
     *
     * @param args 1 argument, which is object to compare if equal
     * @return true if both objects are the same skeleton
     */
    private boolean isEqual(Object[] args) {
        //comparing an initialized object to null, so return false
        if(args[0] == null) {
            return false;
        }

        //If the other object has the same skeleton, return true
        if(args[0].equals(skeleton)) {
            return true;
        } else {
            return false;
        }
    }
}
