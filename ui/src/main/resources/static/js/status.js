/**
 * Created by snow on 5/20/15.
 */
$(function () {
    $(document).ready(function() {
        var interval = 2000
        Highcharts.setOptions({
            global: {
                useUTC: false
            }
        });
        var machines;
        var metricsData;
        $.ajax({
            type: "GET",
            dataType: "json",
            url: ajaxAddress,
            success: function(data){
                machines = Object.keys(data.metricsInfo)
                metricsData = data;
                startGetData()
                /* ---------- CPU ---------- */
                loadCharts($("#cpu"), "CPU Utilization", {
                    title: {
                        text: 'utilization'
                    },
                    min: 0,
                    max: 100
                },
                function(value) {
                    return value.cpuUser * 100
                })
                /* ------------------------- */

                /* ---------- Memory ---------- */
                loadCharts($("#memory"), "Memory Utilization", {
                        title: {
                            text: 'utilization'
                        },
                        min: 0,
                        max: 100
                    },
                    function(value) {
                        return value.memoryUsed / value.memoryTotal * 100
                    })
                /* ------------------------- */

                /* ---------- NetUp ---------- */
                loadCharts($("#net-up"), "Total Network Up", {
                        title: {
                            text: 'speed'
                        },
                        min: 0
                    },
                    function(value) {
                        return value.totalNetworkUp * 1
                    })
                /* ------------------------- */

                /* ---------- NetDown ---------- */
                loadCharts($("#net-down"), "Total Network Down", {
                        title: {
                            text: 'speed'
                        },
                        min: 0
                    },
                    function(value) {
                        return value.totalNetworkDown * 1
                    })
                /* ------------------------- */

            }
        });

        function startGetData() {
            setInterval(function() {
                $.ajax({
                    type: "GET",
                    dataType: "json",
                    url: ajaxAddress,
                    success: function(data) {
                        metricsData = data
                    }
                });
            }, interval);
        }

        function loadCharts(container, title, yAxisConfig, infoFunc) {
            var machineSeriesMap = {};
            var chart;
            container.highcharts({
                chart: {
                    type: 'line',
                    animation: true,
                    marginRight: 10,
                    events: {
                        load: function() {
                            // set up the updating of the chart each second
                            $.each(this.series, function(index, value){
                                machineSeriesMap[value.name] = value
                            })
                            setInterval(function() {
                                $.each(metricsData.metricsInfo, function(key, value){
                                    var x = value.time
                                    var y = infoFunc(value)
                                    machineSeriesMap[key].addPoint([x, y], key == "master" ? true: false, true)
                                })
                            }, 2000);
                        }
                    }
                },
                plotOptions: {
                    series: {
                        showCheckBox: true,
                        marker: {
                            enabled: false
                        }
                    }
                },
                title: {
                    text: title
                },
                xAxis: {
                    type: 'datetime',
                    tickPixelInterval: 150
                },
                yAxis: yAxisConfig,
                tooltip: {
                    formatter: function() {
                        return '<b>'+ this.series.name +'</b><br>'+
                            Highcharts.dateFormat('%Y-%m-%d %H:%M:%S', this.x) +'<br>'+
                            Highcharts.numberFormat(this.y, 2);
                    }
                },
                legend: {
                    enabled: true
                },
                exporting: {
                    enabled: false
                },
                series: machines.map(function(value){
                    var s = {
                        name: value,
                        data: (function() {
                            var data = [],
                                time = (new Date()).getTime(),
                                i;

                            for (i = -50; i <= 0; i++) {
                                data.push({
                                    x: time + i * 2000,
                                    y: 0
                                });
                            }
                            return data;
                            })()
                    }
                    return s
                })
            });
        }
    });
});