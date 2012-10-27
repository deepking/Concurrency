package helper;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

public class Scheduler
{
    private final Timer m_timer;
    
    public Scheduler(Timer timer)
    {
        checkNotNull(timer, "null timer");
        m_timer = timer;
    }
    
    public Timeout schedule(TimerTask task, long initialDelay, long period, TimeUnit unit)
    {
        ContinuousTask continuousTask = new ContinuousTask(task, period, unit);
        Timeout timeout = m_timer.newTimeout(continuousTask, initialDelay, unit);
        ContinuousTimeout ctimeout = continuousTask.init(timeout);
        
        return ctimeout;
    }
    
    public Timeout scheduleOnce(TimerTask task, long delay, TimeUnit unit)
    {
        return m_timer.newTimeout(task, delay, unit);
    }
    
    private static class ContinuousTask implements TimerTask
    {
        private TimerTask m_task;
        private ContinuousTimeout m_timeout;
        private long m_period;
        private TimeUnit m_timeunit;
        
        public ContinuousTask(TimerTask task, long period, TimeUnit unit)
        {
            m_task = task;
            m_period = period;
            m_timeunit = unit;
        }
        
        public ContinuousTimeout init(Timeout timeout)
        {
            m_timeout = new ContinuousTimeout(timeout);
            return m_timeout;
        }

        @Override
        public void run(Timeout timeout) throws Exception
        {
            m_task.run(m_timeout);
            m_timeout.swap(m_timeout.getTimer().newTimeout(this, m_period, m_timeunit));
        }
    }
    
    private static class ContinuousTimeout implements Timeout
    {
        private volatile Timeout m_timeout;
        private volatile boolean m_bIsCancelled = false;
        
        public ContinuousTimeout(Timeout timeout)
        {
            m_timeout = timeout;
        }
        
        void swap(Timeout timeout)
        {
            boolean wasCancelled = isCancelled();
            m_timeout = timeout;
            if (wasCancelled || isCancelled())
                cancel();
        }

        @Override
        public Timer getTimer()
        {
            return m_timeout.getTimer();
        }

        @Override
        public TimerTask getTask()
        {
            return m_timeout.getTask();
        }

        /**
         * never expire
         */
        @Override
        public boolean isExpired()
        {
            return false;
        }

        @Override
        public boolean isCancelled()
        {
            return m_bIsCancelled || m_timeout.isCancelled();
        }

        @Override
        public void cancel()
        {
            m_bIsCancelled = true;
            m_timeout.cancel();
        }
    }
}
