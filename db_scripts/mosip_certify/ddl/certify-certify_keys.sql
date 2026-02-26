-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: inji_certify
-- Table Name : certify_keys
-- Purpose    : Stores issuer metadata (well-known configurations) per config_key
--
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

CREATE TABLE certify_keys (
	config_key varchar NULL,
	config_value text NULL
);

COMMENT ON TABLE certify_keys IS 'Certify Keys: Stores issuer well-known metadata configurations per key (e.g. MGI, INCRA, MDA)';
COMMENT ON COLUMN certify_keys.config_key IS 'Config Key: Identifier for the issuer configuration (e.g. MGI, INCRA, MDA)';
COMMENT ON COLUMN certify_keys.config_value IS 'Config Value: JSON containing the full well-known/credential issuer metadata';
