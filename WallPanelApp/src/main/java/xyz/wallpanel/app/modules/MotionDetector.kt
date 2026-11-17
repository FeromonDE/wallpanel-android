/*
 * Copyright (c) 2022 WallPanel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.wallpanel.app.modules

import android.util.SparseArray

import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Frame
import xyz.wallpanel.app.modules.Motion.Companion.MOTION_DETECTED
import xyz.wallpanel.app.modules.Motion.Companion.MOTION_NOT_DETECTED
import xyz.wallpanel.app.modules.Motion.Companion.MOTION_TOO_DARK

import timber.log.Timber

/**
 * Created by Michael Ritchie on 7/6/18.
 */
class MotionDetector private constructor(private val minLuma: Int, private val motionLeniency: Int) : Detector<Motion>() {

    private val lumaDetector = LumaMotionDetector(minLuma, motionLeniency)

    override fun detect(frame: Frame?): SparseArray<Motion> {
        requireNotNull(frame) { "No frame supplied." }
        val width = frame.metadata.width
        val height = frame.metadata.height
        val result = lumaDetector.detect(frame.grayscaleImageData.array(), width, height)
        val motion = Motion()
        motion.width = width
        motion.height = height
        motion.type = when (result) {
            LumaMotionDetector.TOO_DARK -> MOTION_TOO_DARK
            LumaMotionDetector.MOTION -> MOTION_DETECTED
            else -> MOTION_NOT_DETECTED
        }
        return SparseArray<Motion>(1).apply { put(0, motion) }
    }

    class Builder(private val minLuma: Int, private val motionLeniency: Int) {
        fun build(): MotionDetector {
            return MotionDetector(minLuma, motionLeniency)
        }
    }
}