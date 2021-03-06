/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.array;

import static org.truffleruby.core.array.ArrayHelpers.setSize;
import static org.truffleruby.core.array.ArrayHelpers.setStoreAndSize;

import org.truffleruby.Layouts;
import org.truffleruby.core.array.library.ArrayStoreLibrary;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.objects.shared.PropagateSharingNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic(ArrayGuards.class)
public abstract class ArrayAppendManyNode extends RubyContextNode {

    @Child private PropagateSharingNode propagateSharingNode = PropagateSharingNode.create();

    public abstract DynamicObject executeAppendMany(DynamicObject array, DynamicObject other);

    // Append of a compatible type

    /** Appending an empty array is a no-op, and shouldn't cause an immutable array store to be converted into a mutable
     * one unnecessarily. */
    @Specialization(guards = "isEmptyArray(other)")
    protected DynamicObject appendZero(DynamicObject array, DynamicObject other) {
        return array;
    }

    @Specialization(
            guards = { "!isEmptyArray(other)", "stores.acceptsAllValues(getStore(array), getStore(other))" },
            limit = "STORAGE_STRATEGIES")
    protected DynamicObject appendManySameType(DynamicObject array, DynamicObject other,
            @CachedLibrary("getStore(array)") ArrayStoreLibrary stores,
            @CachedLibrary("getStore(other)") ArrayStoreLibrary otherStores,
            @Cached("createBinaryProfile()") ConditionProfile extendProfile) {
        final int oldSize = Layouts.ARRAY.getSize(array);
        final int otherSize = Layouts.ARRAY.getSize(other);
        final int newSize = oldSize + otherSize;
        final Object store = Layouts.ARRAY.getStore(array);
        final Object otherStore = Layouts.ARRAY.getStore(other);
        final int length = stores.capacity(store);

        propagateSharingNode.executePropagate(array, other);
        if (extendProfile.profile(newSize > length)) {
            final int capacity = ArrayUtils.capacity(getContext(), length, newSize);
            Object newStore = stores.expand(store, capacity);
            otherStores.copyContents(otherStore, 0, newStore, oldSize, otherSize);
            setStoreAndSize(array, newStore, newSize);
        } else {
            otherStores.copyContents(otherStore, 0, store, oldSize, otherSize);
            setSize(array, newSize);
        }
        return array;
    }

    // Generalizations

    @Specialization(
            guards = { "!isEmptyArray(other)", "!stores.acceptsAllValues(getStore(array), getStore(other))" },
            limit = "STORAGE_STRATEGIES")
    protected DynamicObject appendManyGeneralize(DynamicObject array, DynamicObject other,
            @CachedLibrary("getStore(array)") ArrayStoreLibrary stores,
            @CachedLibrary("getStore(other)") ArrayStoreLibrary otherStores) {
        final int oldSize = Layouts.ARRAY.getSize(array);
        final int otherSize = Layouts.ARRAY.getSize(other);
        final int newSize = oldSize + otherSize;
        final Object store = Layouts.ARRAY.getStore(array);
        final Object otherStore = Layouts.ARRAY.getStore(other);
        final Object newStore = stores.generalizeForStore(store, otherStore).allocate(newSize);

        propagateSharingNode.executePropagate(array, other);
        stores.copyContents(store, 0, newStore, 0, oldSize);
        otherStores.copyContents(otherStore, 0, newStore, oldSize, otherSize);
        setStoreAndSize(array, newStore, newSize);
        return array;
    }
}
