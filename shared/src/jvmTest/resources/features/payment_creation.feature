Feature: Payment creation
  Sending a payment validates the input in shared code, calls the backend and, if the backend
  processed it, saves it to the transaction history.

  Scenario: Sending a valid payment
    When I send 10.50 "USD" to "alice@example.com"
    Then the payment is processed
    And the backend processed 1050 minor units in "USD" for "alice@example.com"
    And it is saved to the transaction history
    And the transaction history shows 1 payment to "alice@example.com" of 10.50 "USD"

  Scenario: Rejecting an invalid recipient
    When I send 10.50 "USD" to "not-an-email"
    Then the payment is rejected as an invalid recipient
    And the backend was not called
    And the transaction history is empty