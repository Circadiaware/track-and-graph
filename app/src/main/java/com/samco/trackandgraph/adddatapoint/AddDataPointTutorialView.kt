/*
* This file is part of Track & Graph
*
* Track & Graph is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Track & Graph is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Track & Graph.  If not, see <https://www.gnu.org/licenses/>.
*/
@file:OptIn(ExperimentalPagerApi::class)

package com.samco.trackandgraph.adddatapoint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.samco.trackandgraph.R
import com.samco.trackandgraph.ui.compose.theming.tngColors
import com.samco.trackandgraph.ui.compose.ui.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.threeten.bp.OffsetDateTime

@Composable
fun AddDataPointsTutorial(viewModel: AddDataPointTutorialViewModel) = Column(
    modifier = Modifier.fillMaxSize()
) {
    val currentPage by viewModel.currentPage.observeAsState(0)
    val pagerState = rememberPagerState(initialPage = currentPage)

    HorizontalPager(
        modifier = Modifier.weight(1f),
        count = 3,
        state = pagerState
    ) { page ->
        FadingScrollColumn(modifier = Modifier.fillMaxSize()) {
            when (page) {
                0 -> TutorialPage0()
                1 -> TutorialPage1()
                2 -> TutorialPage2()
            }
        }
    }

    val buttonText =
        if (currentPage == 2) stringResource(R.string.got_it)
        else stringResource(R.string.next)

    //Next button
    WideButton(
        text = buttonText,
        onClick = viewModel::onButtonClicked
    )

    //Synchronise page between view model and view:

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect {
            viewModel.onSwipeToPage(it)
        }
    }

    val scope = rememberCoroutineScope()

    if (currentPage != pagerState.currentPage) {
        LaunchedEffect(currentPage) {
            scope.launch {
                pagerState.animateScrollToPage(currentPage)
            }
        }
    }
}

@Composable
fun TutorialPage2() {
    SpacingLarge()

    Text(
        modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.input_spacing_large)),
        text = stringResource(R.string.data_point_tutorial_page_3_description),
        textAlign = TextAlign.Center,
        fontSize = MaterialTheme.typography.h5.fontSize,
        fontWeight = MaterialTheme.typography.h5.fontWeight
    )

    SpacingLarge()

    //A vector drawing of a graph
    Icon(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        painter = painterResource(id = R.drawable.ic_pie_chart_example),
        contentDescription = null,
        tint = Color.Unspecified
    )

    SpacingLarge()

    Text(
        modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.input_spacing_large)),
        text = stringResource(R.string.data_point_tutorial_page_3_hint),
        textAlign = TextAlign.Center,
        fontSize = MaterialTheme.typography.body2.fontSize,
        fontWeight = MaterialTheme.typography.body2.fontWeight
    )

    SpacingLarge()
}

@Composable
fun TutorialPage1() = Column(
    horizontalAlignment = Alignment.CenterHorizontally
) {
    SpacingLarge()

    Text(
        modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.input_spacing_large)),
        text = stringResource(R.string.data_point_tutorial_page_2_description),
        textAlign = TextAlign.Center,
        fontSize = MaterialTheme.typography.h5.fontSize,
        fontWeight = MaterialTheme.typography.h5.fontWeight
    )

    SpacingLarge()

    //A vector drawing of a graph
    Icon(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        painter = painterResource(id = R.drawable.ic_graph_example),
        tint = MaterialTheme.tngColors.onSurface,
        contentDescription = null
    )

    SpacingLarge()

    Text(
        modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.input_spacing_large)),
        text = stringResource(R.string.data_point_tutorial_page_2_hint),
        textAlign = TextAlign.Center,
        fontSize = MaterialTheme.typography.body2.fontSize,
        fontWeight = MaterialTheme.typography.body2.fontWeight
    )

    SpacingLarge()
}

@Composable
private fun TutorialPage0() {
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpacingSmall()

        Text(
            text = stringResource(R.string.adding_your_first_data_point),
            textAlign = TextAlign.Center,
            fontSize = MaterialTheme.typography.h4.fontSize,
            fontWeight = MaterialTheme.typography.h4.fontWeight
        )

        SpacingLarge()

        Text(
            text = stringResource(R.string.each_data_point_has_a_timestamp_and_value),
            textAlign = TextAlign.Center,
            fontSize = MaterialTheme.typography.subtitle2.fontSize,
            fontWeight = MaterialTheme.typography.subtitle2.fontWeight,
        )

        SpacingLarge()

        Box(
            modifier = Modifier
                .height(intrinsicSize = IntrinsicSize.Max)
                .scale(0.8f)
        ) {
            Column {
                DateTimeButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedDateTime = OffsetDateTime.now(),
                    onDateTimeSelected = {}
                )

                SpacingSmall()

                LabeledRow(label = stringResource(id = R.string.value_colon)) {
                    ValueInputTextField(
                        value = "",
                        onValueChanged = {}
                    )
                }
            }

            //An overlay that fills the parent with a semi transparent background that consumes all click events
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.tngColors.surface.copy(alpha = 0.4f))
                    .clickable(enabled = false, onClick = {})
            )
        }

        SpacingLarge()

        Text(
            text = stringResource(R.string.it_can_also_optionally_have_a_label_and_a_note),
            textAlign = TextAlign.Center,
            fontSize = MaterialTheme.typography.subtitle2.fontSize,
            fontWeight = MaterialTheme.typography.subtitle2.fontWeight,
        )

        SpacingSmall()

        Box(
            modifier = Modifier
                .height(intrinsicSize = IntrinsicSize.Max)
                .scale(0.8f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LabeledRow(label = stringResource(id = R.string.label_colon)) {
                    LabelInputTextField(
                        value = "",
                        onValueChanged = {}
                    )
                }

                SpacingSmall()

                AddANoteButton {}
            }

            //An overlay that fills the parent with a semi transparent background that consumes all click events
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.tngColors.surface.copy(alpha = 0.4f))
                    .clickable(enabled = false, onClick = {})
            )
        }
    }
}