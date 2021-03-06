package conjob.service.job;

import com.codahale.metrics.Clock;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import conjob.config.JobConfig;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

public class RunJobRateLimit implements RunJobLimitMeter {
    private final SlidingTimeWindowReservoir requestTimeWindow;
    @Getter
    private final JobConfig.LimitConfig limitConfig;

    public RunJobRateLimit(JobConfig.LimitConfig limitConfig) {
        this.limitConfig = limitConfig;
        requestTimeWindow = new SlidingTimeWindowReservoir(1, TimeUnit.SECONDS);
    }

    public RunJobRateLimit(JobConfig.LimitConfig limitConfig, Clock metricsClock) {
        this.limitConfig = limitConfig;
        requestTimeWindow = new SlidingTimeWindowReservoir(1, TimeUnit.SECONDS, metricsClock);
    }

    @Override
    public synchronized boolean isAtLimit() {
        return requestTimeWindow.getSnapshot().getValues().length >= limitConfig.getMaxGlobalRequestsPerSecond();
    }

    @Override
    public synchronized void countRun() {
        requestTimeWindow.update(1);
    }

    @Override
    public synchronized void onJobComplete() {
    }
}
