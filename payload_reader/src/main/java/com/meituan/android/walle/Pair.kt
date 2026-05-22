/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.meituan.android.walle

/**
 * Pair of two elements.
 */
class Pair<A, B>(private val mFirst: A, private val mSecond: B) {

    fun getFirst(): A = mFirst

    fun getSecond(): B = mSecond

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (mFirst?.hashCode() ?: 0)
        result = prime * result + (mSecond?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val otherPair = other as Pair<*, *>
        if (mFirst == null) {
            if (otherPair.mFirst != null) {
                return false
            }
        } else if (mFirst != otherPair.mFirst) {
            return false
        }
        if (mSecond == null) {
            if (otherPair.mSecond != null) {
                return false
            }
        } else if (mSecond != otherPair.mSecond) {
            return false
        }
        return true
    }

    companion object {
        fun <A, B> of(first: A, second: B): Pair<A, B> {
            return Pair(first, second)
        }
    }
}
