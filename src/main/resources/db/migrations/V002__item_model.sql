-- Add item_model for CraftEngine ids. Live V001 rows keep legacy model_data; new inserts write item_model.
ALTER TABLE lom.lom2_prototype_kit_items
 ADD COLUMN item_model text NOT NULL DEFAULT '';
