package com.chatbot.eval.model;

import java.util.ArrayList;
import java.util.List;

public class Trace {

    private List<TraceSpan> spans;

    public Trace() {
        this.spans = new ArrayList<>();
    }

    public Trace(List<TraceSpan> spans) {
        this.spans = spans;
    }

    public List<TraceSpan> getSpans() {
        return spans;
    }

    public void setSpans(List<TraceSpan> spans) {
        this.spans = spans;
    }

    public void addSpan(TraceSpan span) {
        this.spans.add(span);
    }
}
