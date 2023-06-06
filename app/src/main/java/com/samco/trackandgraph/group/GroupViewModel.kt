/*
 *  This file is part of Track & Graph
 *
 *  Track & Graph is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Track & Graph is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.samco.trackandgraph.group

import androidx.lifecycle.*
import com.samco.trackandgraph.base.database.dto.*
import com.samco.trackandgraph.base.model.DataInteractor
import com.samco.trackandgraph.base.model.di.DefaultDispatcher
import com.samco.trackandgraph.base.model.di.IODispatcher
import com.samco.trackandgraph.base.model.di.MainDispatcher
import com.samco.trackandgraph.graphstatproviders.GraphStatInteractorProvider
import com.samco.trackandgraph.graphstatview.factories.viewdto.IGraphStatViewData
import com.samco.trackandgraph.util.Stopwatch
import com.samco.trackandgraph.util.flatMapLatestScan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.threeten.bp.Duration
import org.threeten.bp.OffsetDateTime
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GroupViewModel @Inject constructor(
    private val dataInteractor: DataInteractor,
    private val gsiProvider: GraphStatInteractorProvider,
    @IODispatcher private val io: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @MainDispatcher private val ui: CoroutineDispatcher
) : ViewModel() {

    data class DurationInputDialogData(
        val trackerId: Long,
        val duration: Duration
    )

    private val _showDurationInputDialog = MutableLiveData<DurationInputDialogData?>()
    val showDurationInputDialog: LiveData<DurationInputDialogData?> = _showDurationInputDialog

    val hasTrackers: LiveData<Boolean> = dataInteractor
        .hasAtLeastOneTracker()
        .asLiveData(viewModelScope.coroutineContext)

    private val groupId = MutableSharedFlow<Long>(1, 1)

    private val onUpdateChildrenForGroup = dataInteractor.getDataUpdateEvents()
        .onStart { emit(Unit) }
        .flatMapLatest { groupId }

    private val graphChildren = onUpdateChildrenForGroup
        .distinctUntilChanged()
        .map { getGraphObjects(it) }
        .flatMapLatestScan(emptyList<GraphWithViewData>()) { old, new ->
            getGraphViewDataForUpdatedGraphs(old, new)
        }
        .map { graphsToGroupChildren(it) }
        .flowOn(io)

    private fun GraphOrStat.asLoading() = GraphWithViewData(
        this,
        CalculatedGraphViewData(
            System.nanoTime(),
            IGraphStatViewData.loading(this)
        )
    )

    private suspend fun getGraphViewDataForUpdatedGraphs(
        oldGraphs: List<GraphWithViewData>,
        newGraphs: List<GraphOrStat>
    ): Flow<List<GraphWithViewData>> = flow {
        val stopwatch = Stopwatch().apply { start() }
        val oldGraphsById = oldGraphs.associateBy { it.graph.id }

        val pendingUpdates = mutableListOf<GraphWithViewData>()
        val dontUpdate = mutableListOf<GraphWithViewData>()

        for (config in newGraphs) {
            //We don't need to update the view data if the graph hasn't changed
            //display index updates only affect the order of the graphs not the data
            if (oldGraphsById.containsKey(config.id) ||
                oldGraphsById[config.id]?.graph?.copy(displayIndex = 0) != config.copy(displayIndex = 0)
            ) {
                pendingUpdates.add(config.asLoading())
            } else {
                dontUpdate.add(oldGraphsById[config.id]!!)
            }
        }

        emit(dontUpdate + pendingUpdates)

        val batch = mutableListOf<Deferred<GraphWithViewData>>()
        for (pendingUpdate in pendingUpdates) {
            val graph = pendingUpdate.graph
            val viewData = viewModelScope.async(defaultDispatcher) {
                val calculatedData = gsiProvider.getDataFactory(graph.type).getViewData(graph)
                GraphWithViewData(
                    graph,
                    //Shouldn't really need to add one here but it just forces the times to be different
                    // There was a bug previously where the loading and ready states had the same time using
                    // Instant.now() which caused ready states to be missed and infinite loading to be shown
                    CalculatedGraphViewData(System.nanoTime() + 1, calculatedData)
                )
            }
            batch.add(viewData)
        }

        val updated = batch.awaitAll()

        emit(dontUpdate + updated)

        stopwatch.stop()
        Timber.i("Took ${stopwatch.elapsedMillis}ms to generate view data for ${newGraphs.size} graph(s)")
    }

    private val trackersChildren = onUpdateChildrenForGroup
        .distinctUntilChanged()
        .map { getTrackerChildrenAsync(it) }
        .flowOn(io)

    private val groupChildren = onUpdateChildrenForGroup
        .distinctUntilChanged()
        .map { getGroupChildrenAsync(it) }
        .flowOn(io)

    val allChildren: LiveData<List<GroupChild>> =
        combine(graphChildren, trackersChildren, groupChildren) { graphs, trackers, groups ->
            val children = mutableListOf<GroupChild>().apply {
                addAll(graphs)
                addAll(trackers)
                addAll(groups)
            }
            sortChildren(children)
            return@combine children
        }.flowOn(io).asLiveData(viewModelScope.coroutineContext)

    val trackers
        get() = allChildren.value
            ?.filterIsInstance<GroupChild.ChildTracker>()
            ?.map { it.displayTracker }
            ?: emptyList()

    fun setGroup(groupId: Long) {
        viewModelScope.launch { this@GroupViewModel.groupId.emit(groupId) }
    }

    private fun sortChildren(children: MutableList<GroupChild>) = children.sortWith { a, b ->
        val aInd = a.displayIndex
        val bInd = b.displayIndex
        when {
            aInd < bInd -> -1
            bInd < aInd -> 1
            else -> {
                val aId = a.id
                val bId = b.id
                when {
                    aId > bId -> -1
                    bId > aId -> 1
                    else -> 0
                }
            }
        }
    }

    private suspend fun getTrackerChildrenAsync(groupId: Long): List<GroupChild> {
        return dataInteractor.getDisplayTrackersForGroupSync(groupId).map {
            GroupChild.ChildTracker(it.id, it.displayIndex, it)
        }
    }

    private suspend fun getGroupChildrenAsync(groupId: Long): List<GroupChild> {
        return dataInteractor.getGroupsForGroupSync(groupId).map {
            GroupChild.ChildGroup(it.id, it.displayIndex, it)
        }
    }

    private suspend fun getGraphObjects(groupId: Long): List<GraphOrStat> {
        val graphStats = dataInteractor.getGraphsAndStatsByGroupIdSync(groupId)
        return graphStats.filter { !gsiProvider.getDataSourceAdapter(it.type).preen(it) }
    }

    private fun graphsToGroupChildren(graphs: List<GraphWithViewData>): List<GroupChild> {
        return graphs.map {
            GroupChild.ChildGraph(it.graph.id, it.graph.displayIndex, it.viewData)
        }
    }

    fun addDefaultTrackerValue(tracker: DisplayTracker) = viewModelScope.launch(io) {
        val newDataPoint = DataPoint(
            timestamp = OffsetDateTime.now(),
            featureId = tracker.featureId,
            value = tracker.defaultValue,
            label = tracker.defaultLabel,
            note = ""
        )
        dataInteractor.insertDataPoint(newDataPoint)
    }

    fun onDeleteFeature(id: Long) = viewModelScope.launch(io) {
        dataInteractor.deleteFeature(id)
    }

    fun adjustDisplayIndexes(items: List<GroupChild>) = viewModelScope.launch(io) {
        groupId.take(1).collect {
            dataInteractor.updateGroupChildOrder(it, items.map(GroupChild::toDto))
        }
    }

    fun onDeleteGraphStat(id: Long) =
        viewModelScope.launch(io) { dataInteractor.deleteGraphOrStat(id) }

    fun onDeleteGroup(id: Long) =
        viewModelScope.launch(io) { dataInteractor.deleteGroup(id) }

    fun duplicateGraphOrStat(graphOrStatViewData: IGraphStatViewData) {
        viewModelScope.launch(io) {
            val gs = graphOrStatViewData.graphOrStat
            gsiProvider.getDataSourceAdapter(gs.type).duplicateGraphOrStat(gs)
        }
    }

    fun onConsumedShowDurationInputDialog() {
        _showDurationInputDialog.value = null
    }

    fun stopTimer(tracker: DisplayTracker) = viewModelScope.launch(io) {
        dataInteractor.stopTimerForTracker(tracker.id)?.let {
            withContext(ui) {
                _showDurationInputDialog.value = DurationInputDialogData(tracker.id, it)
            }
        }
    }

    fun playTimer(tracker: DisplayTracker) = viewModelScope.launch(io) {
        dataInteractor.playTimerForTracker(tracker.id)
    }
}