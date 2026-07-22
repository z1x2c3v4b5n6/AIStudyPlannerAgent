package com.yhk.aistudyplanner.ai.gateway;

import com.yhk.aistudyplanner.common.exception.ErrorCode;

public class AiProviderException extends RuntimeException {
  private final ErrorCode code;
  private final String category;

  public AiProviderException(ErrorCode code) {
    this(code, code.name());
  }

  public AiProviderException(ErrorCode code, AiFailureCategory category) {
    this(code, category.name());
  }

  private AiProviderException(ErrorCode code, String category) {
    super(code.name());
    this.code = code;
    this.category = category;
  }

  public ErrorCode code() {
    return code;
  }

  public String category() {
    return category;
  }
}
