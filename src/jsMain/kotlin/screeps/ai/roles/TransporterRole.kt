package screeps.ai.roles

import screeps.api.Creep
import screeps.api.ERR_NOT_ENOUGH_ENERGY
import screeps.api.ERR_NOT_FOUND
import screeps.api.ERR_NOT_IN_RANGE
import screeps.api.FIND_MY_STRUCTURES
import screeps.api.OK
import screeps.api.RESOURCE_ENERGY
import screeps.api.STRUCTURE_EXTENSION
import screeps.api.STRUCTURE_SPAWN
import screeps.api.STRUCTURE_STORAGE
import screeps.api.STRUCTURE_TOWER
import screeps.api.StoreOwner
import screeps.api.compareTo
import screeps.api.structures.Structure
import screeps.sdk.ScreepsLog
import kotlin.math.abs

val FILLABLE_STRUCTURES = setOf(
    STRUCTURE_SPAWN,
    STRUCTURE_EXTENSION,
    STRUCTURE_TOWER,
    STRUCTURE_STORAGE
)

class TransporterRole(creep: Creep) : AbstractRole(creep) {

    companion object {
        private const val TAG = "TransporterRole"
    }

    override fun run() {
        when (state) {
            CreepState.GET_ENERGY -> {
                getEnergy()
            }

            CreepState.DO_WORK -> {
                storeEnergy()
            }
        }
    }

    private fun getEnergy() {
        val status = pickupEnergy()

        if (status == ERR_NOT_FOUND) {
            val storage = creep.room.storage
            if (storage == null || storage.store.getUsedCapacity(RESOURCE_ENERGY) <= 0) {
                say("No energy could be found in room")
                // Try to transport whatever energy we do have while waiting on more to be generated
                if (creep.store.getUsedCapacity(RESOURCE_ENERGY) > 50) {
                    state = CreepState.DO_WORK
                }
                return
            }
            ScreepsLog.d(TAG, "No energy to pick up, gathering from storage")
            val code = creep.withdraw(storage, RESOURCE_ENERGY)
            if (code == ERR_NOT_IN_RANGE) {
                creep.moveTo(storage.pos.x, storage.pos.y)
            } else if (status != OK) {
                ScreepsLog.d(TAG, "Storage withdraw failed with code $status")
            }
        }

        if (creep.store.getFreeCapacity() == 0) {
            say("Energy full")
            state = CreepState.DO_WORK
        }
    }

    private fun findFillableStructures(): List<StoreOwner> {
        val fillableStructures = creep.room.find(FIND_MY_STRUCTURES).filter {
            it.structureType in FILLABLE_STRUCTURES
        }.map { it as StoreOwner }.filter {
            (it.store.getFreeCapacity(RESOURCE_ENERGY) ?: 0) > 0
        }.groupBy {
            when (it.unsafeCast<Structure>().structureType) {
                // TODO: Determine priority level more intelligently
                STRUCTURE_SPAWN -> 1
                STRUCTURE_EXTENSION -> 1
                STRUCTURE_TOWER -> 2
                STRUCTURE_STORAGE -> 3
                else -> 4
            }
        }

        return fillableStructures.getOrElse(fillableStructures.keys.minOrNull() ?: 2) { emptyList() }
    }

    private fun storeEnergy() {
        val fillableStructures = findFillableStructures()

        if (fillableStructures.isEmpty()) {
            ScreepsLog.d(TAG, "No structures to fill with energy")
            return
        }

        val structureType = (fillableStructures[0] as Structure).structureType
        val fillableStructure = if (structureType == STRUCTURE_TOWER) {
            fillableStructures.maxByOrNull { it.store.getFreeCapacity(RESOURCE_ENERGY) ?: 0 }
        } else {
            fillableStructures.minByOrNull { abs(it.pos.x - creep.pos.x) + abs(it.pos.y - creep.pos.y) }
        }

        if (fillableStructure == null) {
            ScreepsLog.d(TAG, "No structures to fill with energy!")
            return
        }

        val status = creep.transfer(fillableStructure, RESOURCE_ENERGY)

        if (status == ERR_NOT_IN_RANGE) {
            creep.moveTo(fillableStructure)
        } else if (status == ERR_NOT_ENOUGH_ENERGY) {
            say("Out of energy")
            state = CreepState.GET_ENERGY
            return
        } else if (status != OK) {
            say("Transfer failed with code $status")
        }

        if (creep.store.getUsedCapacity(RESOURCE_ENERGY) <= 0) {
            state = CreepState.GET_ENERGY
        }
    }
}