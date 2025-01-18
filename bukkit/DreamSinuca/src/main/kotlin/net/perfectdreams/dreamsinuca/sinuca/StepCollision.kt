package net.perfectdreams.dreamsinuca.sinuca

import org.dyn4j.dynamics.Body
import org.dyn4j.geometry.Vector2

data class StepCollision(val body1: Body, val body2: Body, val point: Vector2, val depth: Double)