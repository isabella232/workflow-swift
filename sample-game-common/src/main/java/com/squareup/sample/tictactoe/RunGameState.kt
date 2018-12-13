/*
 * Copyright 2017 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sample.tictactoe

import com.squareup.sample.tictactoe.SyncState.SAVING
import com.squareup.workflow.Delegating
import com.squareup.workflow.Snapshot
import com.squareup.workflow.makeWorkflowId
import com.squareup.workflow.parse
import com.squareup.workflow.readByteStringWithLength
import com.squareup.workflow.readUtf8WithLength
import com.squareup.workflow.writeByteStringWithLength
import com.squareup.workflow.writeUtf8WithLength
import okio.ByteString
import kotlin.reflect.jvm.jvmName

/**
 * The state of a [RunGameReactor].
 */
sealed class RunGameState {
  internal data class Playing(override val delegateState: Turn) : RunGameState(),
      Delegating<Turn, TakeTurnsEvent, CompletedGame> {
    override val id = TakeTurnsReactor::class.makeWorkflowId()
  }

  internal data class NewGame(
    val defaultXName: String = "X",
    val defaultOName: String = "O"
  ) : RunGameState()

  internal data class MaybeQuitting(val completedGame: CompletedGame) : RunGameState()

  data class GameOver(
    val completedGame: CompletedGame,
    val syncState: SyncState = SAVING
  ) : RunGameState()

  fun toSnapshot(): Snapshot {
    return Snapshot.write { sink ->
      sink.writeUtf8WithLength(this::class.jvmName)

      when (this) {
        is Playing -> {
          sink.writeByteStringWithLength(delegateState.toSnapshot().bytes)
        }
        is NewGame -> {
          sink.writeUtf8WithLength(defaultXName)
          sink.writeUtf8WithLength(defaultOName)
        }
        is MaybeQuitting -> sink.writeByteStringWithLength(completedGame.toSnapshot().bytes)
        is GameOver -> sink.writeByteStringWithLength(completedGame.toSnapshot().bytes)
      }
    }
  }

  companion object {
    fun start(): RunGameState = NewGame()

    fun fromSnapshot(byteString: ByteString): RunGameState {
      byteString.parse { source ->
        val className = source.readUtf8WithLength()

        return when (className) {
          Playing::class.jvmName -> Playing(
              Turn.fromSnapshot(
                  source.readByteStringWithLength()
              )
          )

          NewGame::class.jvmName -> NewGame(
              source.readUtf8WithLength(),
              source.readUtf8WithLength()
          )

          MaybeQuitting::class.jvmName -> MaybeQuitting(
              CompletedGame.fromSnapshot(
                  source.readByteStringWithLength()
              )
          )

          GameOver::class.jvmName -> GameOver(
              CompletedGame.fromSnapshot(
                  source.readByteStringWithLength()
              )
          )

          else -> throw IllegalArgumentException("Unknown type $className")
        }
      }
    }
  }
}

/**
 * Sub-state of [RunGameState.GameOver], indicates if we're in the process of saving the game,
 * or if not, how that went.
 */
enum class SyncState {
  SAVING,
  SAVE_FAILED,
  SAVED
}