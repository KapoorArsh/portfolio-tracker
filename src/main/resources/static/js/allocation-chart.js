'use strict';

/**
 * allocation-chart.js
 *
 * Renders a Chart.js doughnut showing each holding's percentage of total
 * portfolio value.  Runs on the portfolio detail page only.
 *
 * WHY fetch() instead of inlining the data in the Thymeleaf template?
 *   GET /api/portfolios/{id}/summary is the single source of truth for
 *   computed portfolio analytics.  Reusing it here means the chart data
 *   is always consistent with the REST API response — no duplication of
 *   the allocation calculation in the view layer.
 *
 * Data flow:
 *   1. Read portfolio id from  <canvas data-portfolio-id="...">
 *   2. GET /api/portfolios/{id}/summary
 *   3. Extract summary.allocation  →  { "AAPL": 48.72, "MSFT": 51.28 }
 *   4. Pass labels + data to new Chart(canvas, { type: 'doughnut', ... })
 */

/*
 * The script tag is at the bottom of <body>, so the DOM is fully parsed by the
 * time this executes.  DOMContentLoaded is therefore redundant here, but is
 * kept as a belt-and-suspenders guard in case this file is ever moved to <head>.
 */
document.addEventListener('DOMContentLoaded', function () {

    var canvas   = document.getElementById('allocation-chart');
    var statusEl = document.getElementById('chart-status');

    /* Guard: if the canvas doesn't exist we are not on the detail page. */
    if (!canvas) { return; }

    /*
     * Read the portfolio id that Thymeleaf baked into the data attribute:
     *   th:attr="data-portfolio-id=${portfolio.id}"
     * dataset converts "data-portfolio-id" → camelCase "portfolioId".
     */
    var portfolioId = canvas.dataset.portfolioId;

    fetch('/api/portfolios/' + portfolioId + '/summary')
        .then(function (response) {
            if (!response.ok) {
                throw new Error('Server returned HTTP ' + response.status);
            }
            return response.json();
        })
        .then(function (summary) {
            /*
             * summary.allocation is a JSON object whose keys are ticker symbols
             * and whose values are percentage floats that sum to ~100.
             * e.g. { "AAPL": 48.72, "MSFT": 51.28 }
             */
            var allocation = summary.allocation;
            var tickers    = Object.keys(allocation);

            if (tickers.length === 0) {
                if (statusEl) { statusEl.textContent = 'No holdings to chart.'; }
                return;
            }

            /* Remove the "Loading…" placeholder once we have real data. */
            if (statusEl) { statusEl.remove(); }

            var values = tickers.map(function (t) { return allocation[t]; });

            /*
             * Colour palette — 10 distinct indigo/teal/green/amber tones that
             * match the CSS design tokens.  Sliced to the number of actual tickers
             * so we never request a colour that doesn't exist.
             */
            var PALETTE = [
                '#4F46E5', /* indigo-600 */
                '#0891B2', /* cyan-600   */
                '#059669', /* emerald-600*/
                '#D97706', /* amber-600  */
                '#DC2626', /* red-600    */
                '#7C3AED', /* violet-600 */
                '#DB2777', /* pink-600   */
                '#65A30D', /* lime-600   */
                '#2563EB', /* blue-600   */
                '#0D9488'  /* teal-600   */
            ];

            new Chart(canvas, {
                type: 'doughnut',
                data: {
                    labels: tickers,
                    datasets: [{
                        data:            values,
                        backgroundColor: PALETTE.slice(0, tickers.length),
                        borderWidth:     2,
                        borderColor:     '#ffffff'
                    }]
                },
                options: {
                    responsive:          true,
                    maintainAspectRatio: true,
                    /*
                     * cutout: '60%' makes the hole large enough to show a clean
                     * ring rather than a solid pie.  60% is the Chart.js default
                     * for doughnuts; stated explicitly here for clarity.
                     */
                    cutout: '60%',
                    plugins: {
                        legend: {
                            position: 'right',
                            labels: {
                                boxWidth: 14,
                                padding:  16,
                                font:     { size: 13 }
                            }
                        },
                        tooltip: {
                            callbacks: {
                                /*
                                 * ctx.parsed is the raw numeric value (the percentage).
                                 * Prepend two spaces so the label doesn't hug the edge.
                                 */
                                label: function (ctx) {
                                    return '  ' + ctx.label + ':  ' + ctx.parsed.toFixed(2) + '%';
                                }
                            }
                        }
                    }
                }
            });
        })
        .catch(function (err) {
            console.error('[allocation-chart] Failed to load chart data:', err);
            if (statusEl) {
                statusEl.textContent = 'Could not load allocation data.';
                statusEl.style.color = 'var(--loss)';
            }
        });
});

