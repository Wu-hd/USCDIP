package com.uscdip.backend.service;

import com.uscdip.backend.entity.OutboxEventEntity;

public interface OutboxDispatcher {

    OutboxDispatchResult dispatch(OutboxEventEntity event);
}
