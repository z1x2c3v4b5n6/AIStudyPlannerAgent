package com.yhk.aistudyplanner.common.response;

import java.util.List;

public record PageResponse<T>(List<T> list, long page, long pageSize, long total) {
}

