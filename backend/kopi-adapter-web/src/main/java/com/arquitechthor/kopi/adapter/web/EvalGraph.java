package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// The evaluation graph of the review page, drawn on the server as SVG
/// (plan §8.5): White's Win % position by position, from the start (left)
/// to the end (right), 100 % at the top.
///
/// Only geometry here; the template draws it. Win % rather than centipawns,
/// like the classification (ADR-005): a won position stays near the top
/// instead of running off the chart.
///
/// @param width   the drawing's width, in SVG units
/// @param height  its height
/// @param line    the `points` of the polyline
/// @param area    the `d` of the shape under it: White's share
/// @param markers the moves worth spotting: mistakes, blunders, missed mates
record EvalGraph(int width, int height, String line, String area, List<Marker> markers) {

    static final int WIDTH = 600;
    static final int HEIGHT = 120;

    /// What gets a dot on the line.
    private static final Set<MoveCategory> MARKED = Set.of(MoveCategory.MISTAKE, MoveCategory.BLUNDER,
            MoveCategory.MISSED_MATE);

    /// A dot on the line, after the move it marks.
    ///
    /// @param ply      the move
    /// @param x        where
    /// @param y        where
    /// @param category what kind of move (its CSS class)
    record Marker(int ply, String x, String y, MoveCategory category) {}

    /// @param positions  the evaluation of every position, the starting one
    ///                   included: one more than the moves
    /// @param categories the category of every move
    static EvalGraph of(List<Evaluation> positions, List<MoveCategory> categories) {
        if (positions.size() != categories.size() + 1) {
            throw new IllegalArgumentException("One more position than moves");
        }
        var steps = Math.max(1, positions.size() - 1);
        var points = new ArrayList<String>();
        var markers = new ArrayList<Marker>();
        for (int i = 0; i < positions.size(); i++) {
            var x = number(WIDTH * (double) i / steps);
            var y = number(HEIGHT * (1 - positions.get(i).winChanceFor(Color.WHITE).percent() / 100));
            points.add(x + "," + y);
            if (i > 0 && MARKED.contains(categories.get(i - 1))) {
                markers.add(new Marker(i, x, y, categories.get(i - 1)));
            }
        }
        var line = String.join(" ", points);
        var area = "M0," + HEIGHT + " L" + String.join(" L", points) + " L" + number(WIDTH) + "," + HEIGHT + " Z";
        return new EvalGraph(WIDTH, HEIGHT, line, area, markers);
    }

    /// SVG wants dots for decimals, whatever the visitor's language.
    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
