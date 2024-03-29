#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#

import traceback
from datetime import datetime
from typing import Optional

from airbyte_cdk.models import (
    AirbyteConnectionStatus,
    AirbyteErrorTraceMessage,
    AirbyteMessage,
    AirbyteTraceMessage,
    FailureType,
    Status,
    StreamDescriptor,
    TraceType,
)
from airbyte_cdk.models import Type as MessageType
from airbyte_cdk.utils.airbyte_secrets_utils import filter_secrets


class AirbyteTracedException(Exception):
    """
    An exception that should be emitted as an AirbyteTraceMessage
    """

    def __init__(
        self,
        internal_message: Optional[str] = None,
        message: Optional[str] = None,
        failure_type: FailureType = FailureType.system_error,
        exception: Optional[BaseException] = None,
    ):
        """
        :param internal_message: the internal error that caused the failure
        :param message: a user-friendly message that indicates the cause of the error
        :param failure_type: the type of error
        :param exception: the exception that caused the error, from which the stack trace should be retrieved
        """
        self.internal_message = internal_message
        self.message = message
        self.failure_type = failure_type
        self._exception = exception
        super().__init__(internal_message)

    def as_airbyte_message(self, stream_descriptor: StreamDescriptor = None) -> AirbyteMessage:
        """
        Builds an AirbyteTraceMessage from the exception
        """
        now_millis = datetime.now().timestamp() * 1000.0

        trace_exc = self._exception or self
        stack_trace_str = "".join(traceback.TracebackException.from_exception(trace_exc).format())

        trace_message = AirbyteTraceMessage(
            type=TraceType.ERROR,
            emitted_at=now_millis,
            error=AirbyteErrorTraceMessage(
                message=self.message or "Something went wrong in the connector. See the logs for more details.",
                internal_message=self.internal_message,
                failure_type=self.failure_type,
                stack_trace=stack_trace_str,
                stream_descriptor=stream_descriptor,
            ),
        )

        return AirbyteMessage(type=MessageType.TRACE, trace=trace_message)

    def as_connection_status_message(self) -> AirbyteMessage:
        if self.failure_type == FailureType.config_error:
            output_message = AirbyteMessage(
                type=MessageType.CONNECTION_STATUS, connectionStatus=AirbyteConnectionStatus(status=Status.FAILED, message=self.message)
            )
            return output_message

    def emit_message(self) -> None:
        """
        Prints the exception as an AirbyteTraceMessage.
        Note that this will be called automatically on uncaught exceptions when using the airbyte_cdk entrypoint.
        """
        message = self.as_airbyte_message().json(exclude_unset=True)
        filtered_message = filter_secrets(message)
        print(filtered_message)

    @classmethod
    def from_exception(cls, exc: BaseException, *args, **kwargs) -> "AirbyteTracedException":  # type: ignore  # ignoring because of args and kwargs
        """
        Helper to create an AirbyteTracedException from an existing exception
        :param exc: the exception that caused the error
        """
        return cls(internal_message=str(exc), exception=exc, *args, **kwargs)  # type: ignore  # ignoring because of args and kwargs

    def as_sanitized_airbyte_message(self, stream_descriptor: StreamDescriptor = None) -> AirbyteMessage:
        """
        Builds an AirbyteTraceMessage from the exception and sanitizes any secrets from the message body
        """
        error_message = self.as_airbyte_message(stream_descriptor=stream_descriptor)
        if error_message.trace.error.message:
            error_message.trace.error.message = filter_secrets(error_message.trace.error.message)
        if error_message.trace.error.internal_message:
            error_message.trace.error.internal_message = filter_secrets(error_message.trace.error.internal_message)
        if error_message.trace.error.stack_trace:
            error_message.trace.error.stack_trace = filter_secrets(error_message.trace.error.stack_trace)
        return error_message
