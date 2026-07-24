/*
 * Copyright 2026 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.river.api.io;

import java.io.InvalidClassException;
import java.rmi.RemoteException;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@link ThrowableSerializer#captureMessage} robustness (review gate,
 * fix/der-throwable-marshalling): the decoration-stripping branches assume the
 * standard JDK {@code getMessage()} decoration shape, but a subclass overriding
 * {@code getMessage()} can return {@code null} or an undecorated string.
 * Capture runs on the fault-marshalling path -- {@code DerThrowableForm.capture}
 * invokes it for every node of every carried fault -- so a
 * {@code StringIndexOutOfBoundsException}/NPE here would replace the fault with
 * a connection abort (the exact failure U1b finding 8a removes). These pins
 * assert the strip degrades to the message as-is instead of throwing.
 */
public class ThrowableSerializerCaptureMessageTest {

    /** A RemoteException subclass whose overridden getMessage drops the JDK decoration. */
    static class UndecoratedRemoteException extends RemoteException {
        private static final long serialVersionUID = 1L;
        UndecoratedRemoteException(String s, Throwable cause) { super(s, cause); }
        @Override
        public String getMessage() { return "custom: no decoration"; }
    }

    /** A RemoteException subclass whose overridden getMessage returns null. */
    static class NullMessageRemoteException extends RemoteException {
        private static final long serialVersionUID = 1L;
        NullMessageRemoteException(Throwable cause) { super("x", cause); }
        @Override
        public String getMessage() { return null; }
    }

    /** Standard decorated RemoteException: decoration stripped to the original message. */
    @Test
    public void remoteExceptionDecorationStripped() {
        RemoteException e = new RemoteException("remote boom",
                new IllegalStateException("under"));
        Assert.assertEquals("remote boom", ThrowableSerializer.captureMessage(e));
    }

    /** Overridden getMessage without the decoration: message as-is, no SIOOBE. */
    @Test
    public void undecoratedRemoteExceptionSubclassDoesNotThrow() {
        RemoteException e = new UndecoratedRemoteException("ignored",
                new IllegalStateException("under"));
        Assert.assertEquals("custom: no decoration",
                ThrowableSerializer.captureMessage(e));
    }

    /** Overridden getMessage returning null with detail set: null, no NPE. */
    @Test
    public void nullMessageRemoteExceptionSubclassDoesNotThrow() {
        RemoteException e = new NullMessageRemoteException(
                new IllegalStateException("under"));
        Assert.assertNull(ThrowableSerializer.captureMessage(e));
    }

    /** Standard InvalidClassException: classname prefix stripped. */
    @Test
    public void invalidClassExceptionPrefixStripped() {
        InvalidClassException e = new InvalidClassException("com.example.Foo", "bad svuid");
        Assert.assertEquals("bad svuid", ThrowableSerializer.captureMessage(e));
    }

    /**
     * The full fault path stays crash-free: capture of a fault tree whose cause
     * is a hostile-getMessage RemoteException subclass must produce a carrier
     * (never throw), preserving the overridden message.
     */
    @Test
    public void derCaptureSurvivesHostileGetMessageSubclass() {
        Exception fault = new Exception("primary",
                new UndecoratedRemoteException("ignored", new IllegalStateException("under")));
        fault.addSuppressed(new NullMessageRemoteException(new RuntimeException("r")));

        DerThrowableForm form = DerThrowableForm.capture(fault);

        Assert.assertEquals("primary", form.message());
        Assert.assertEquals("custom: no decoration", form.cause().message());
        Assert.assertEquals(1, form.suppressed().length);
        Assert.assertNull(form.suppressed()[0].message());
    }
}
