package com.yhk.aistudyplanner.ai.gateway;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
public class AiProviderException extends RuntimeException {private final ErrorCode code;public AiProviderException(ErrorCode code){super(code.name());this.code=code;}public ErrorCode code(){return code;}}
