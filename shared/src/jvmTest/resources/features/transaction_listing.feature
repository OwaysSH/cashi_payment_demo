Feature: Transaction listing
  The transaction history lists processed payments newest first and updates in real time.

  Scenario: History shows sent payments newest first and updates in real time
    Given I have sent 10.00 "USD" to "first@example.com"
    And I have sent 25.50 "EUR" to "second@example.com"
    When I open the transaction history
    Then I see these payments, newest first:
      | recipient          | amount | currency |
      | second@example.com | 25.50  | EUR      |
      | first@example.com  | 10.00  | USD      |
    When I send 5.00 "USD" to "third@example.com" while viewing the history
    Then "third@example.com" appears at the top of the history