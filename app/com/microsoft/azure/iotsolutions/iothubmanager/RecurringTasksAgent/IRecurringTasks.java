// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.RecurringTasksAgent;

import com.google.inject.ImplementedBy;

@ImplementedBy(RecurringTasks.class)
public interface IRecurringTasks {
    void run();
}
