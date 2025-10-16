package com.patternalarm.flinkprocessor.model

/**
 * Fraud pattern types detected across all domains
 */
object FraudType extends Enumeration {
  type FraudType = Value

  val VelocitySpike = Value("velocity_spike")
  val AccountTakeover = Value("account_takeover")
  val ChargebackFraud = Value("chargeback_fraud")
  val GoldFarming = Value("gold_farming")
  val Structuring = Value("structuring")
  val MoneyLaundering = Value("money_laundering")
  val CardTesting = Value("card_testing")
  val PromoAbuse = Value("promo_abuse")
  val FriendlyFraud = Value("friendly_fraud")
  val Unknown = Value("unknown_pattern")

  /**
   * Parse fraud type from pattern string
   */
  def fromPattern(pattern: String): FraudType = pattern.toLowerCase match {
    case p if p.contains("velocity") => VelocitySpike
    case p if p.contains("takeover") => AccountTakeover
    case p if p.contains("chargeback") => ChargebackFraud
    case p if p.contains("gold_farming") => GoldFarming
    case p if p.contains("structuring") => Structuring
    case p if p.contains("laundering") => MoneyLaundering
    case p if p.contains("card_testing") => CardTesting
    case p if p.contains("promo_abuse") => PromoAbuse
    case p if p.contains("friendly_fraud") => FriendlyFraud
    case _ => Unknown
  }
}