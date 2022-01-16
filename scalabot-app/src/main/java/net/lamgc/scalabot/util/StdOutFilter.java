package net.lamgc.scalabot.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * 标准输出过滤器.
 *
 * <p> LogBack 在性能上虽然很好，但是自带的 ThresholdFilter 竟然不支持过滤 WARN 以下等级的日志！
 * 重点是，加两个参数就可以实现这个过滤功能了（加一个 onMatch 和 onMismatch 就可以了）！
 * 绝了。
 *
 * @author LamGC
 */
public class StdOutFilter extends AbstractMatcherFilter<LoggingEvent> {

    private final static int maxLevel = Level.INFO_INT;

    @Override
    public FilterReply decide(LoggingEvent event) {
        int levelInt = event.getLevel().levelInt;
        return levelInt <= maxLevel ? FilterReply.ACCEPT : FilterReply.DENY;
    }
}
