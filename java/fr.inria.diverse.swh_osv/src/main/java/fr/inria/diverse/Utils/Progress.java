package fr.inria.diverse.Utils;


import org.apache.logging.log4j.Logger;

import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;

public class Progress {

    public static ProgressBarBuilder infoBarBuilder(String taskName, Logger logger) {
        return new ProgressBarBuilder()
                .setTaskName(taskName)
                .setConsumer(new DelegatingProgressBarConsumer(logger::info));

    }

    public static ProgressBar infoBar(String taskName, Logger logger,int size) {
        return new ProgressBarBuilder()
                .setTaskName(taskName)
                .setInitialMax(size)
                .setConsumer(new DelegatingProgressBarConsumer(logger::info)).build();

    }

    public static ProgressBar infoBar(String taskName, Logger logger,long size) {
        return new ProgressBarBuilder()
                .setTaskName(taskName)
                .setInitialMax(size)
                .setConsumer(new DelegatingProgressBarConsumer(logger::info)).build();

    }
}
