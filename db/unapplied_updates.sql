-- Pending schema/data changes for the next production release.
-- Process, conventions, and how to fold local admin/UI edits (e.g. Topic
-- Board layout changes made in the running app) back into this file before
-- a refresh discards them: see docs/database-release-practice.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Move the IVC historical-meeting attachments from local file storage to Azure Blob.
-- Task: docs/tasks/ivc-historical-meetings-migration-report.md. The objects are
-- uploaded by docs/tasks/migrate_ivc_archive_to_blob.sh, which verifies each file
-- against the SHA-256 in docs/tasks/ivc-historical-meetings-files.tsv and reads it
-- back anonymously before this runs. Upload the objects FIRST: this statement only
-- repoints the rows at bytes that already exist in the container.
--
-- The storage_key does not change. The Blob object is uploaded under the same key
-- the file already had locally, so public_id (the stable URL identifier) and
-- storage_key both stay as they are and the /hub/files/<id> URLs are unaffected.
-- Replacements and preserved versions continue to follow the recorded backend.
--
-- Guarded on storage_backend='LOCAL', so this is a no-op for rows that are already
-- on Blob and can be re-run safely.

UPDATE hub_stored_file
SET storage_backend = 'BLOB',
    blob_endpoint = 'https://testsabbiastorage.blob.core.windows.net',
    blob_container = 'artifacts'
WHERE storage_backend = 'LOCAL'
  AND public_id IN (
    'ef1e8b6d-f977-4760-8236-1b9638cd5632',
    'fd5747ed-fe79-46c8-a1fe-f68bb8211156',
    'fd26a179-7367-4010-ab8b-6e365886873c',
    'd573a657-faf6-491e-8071-d52a7edbdbbd',
    '28a1e6d3-e4eb-4c38-94ee-95ee869e5b59',
    '9ec2dcad-fa0c-48be-a9e3-073b50ac642e',
    '6a6e2b95-f323-464b-9671-2194d7242b92',
    'b40f059a-9862-4519-9435-4eb682b414de',
    '0d542e20-40d5-4c9f-81d4-23f565cf7693',
    '4f400323-3ce5-4590-a92c-e9f40d141de9',
    'af9d68b7-7657-485e-9889-fc1f09449d1e',
    '073a804c-fe33-4b63-9faf-c62375173fbb',
    '3fac4989-a984-4ccb-b081-04ff5a49154a',
    '769bdc87-66e3-4acd-92f6-cafd08302f08',
    'ff10ca07-ce40-4eed-929a-a53b10b5b3b8',
    '927a8049-93e7-4c91-adb3-6b9bf280de0f',
    '97542530-5fa3-491c-ad9a-88fe55de922c',
    '5a420935-3f94-4d7d-b3c4-66ea1bfa7648',
    '13f827f1-19e8-4e67-ba81-51c72d4299dc',
    '86fc0392-9b0f-470f-b7a6-a06a452d5371',
    '97604564-c91a-479b-9d82-ff557abaace7',
    '3bb9af1d-94b9-4313-985e-7952ad9e3941',
    '74b88ed7-22a8-4efa-b1e4-2d1de5b6cc11',
    'f5f83daa-f515-48ed-9e84-605a7867d96b',
    '81d8a650-4ece-4239-a0f0-1079f0921868',
    '1a983f34-9e63-4a58-b7f8-1a4611ae1943',
    'ee7067e8-7628-46cf-8fca-a6ee636f7825',
    'bccc2cc6-168c-47ae-9817-6a4146ca4872',
    '920b504a-59b2-4caa-927f-bdca04f20c4a',
    'a8afc867-8430-4a58-8b54-6598777b7a77',
    'bb631c76-278a-4bb7-933a-534d1a71f438',
    'fe9a5ef0-c26c-45f2-aa4e-852895a48e33',
    '85a08544-db55-4fe2-9304-ec70c662e6eb',
    '61253c43-b3be-49e6-8137-2360799bd73c',
    'c31f73ec-9095-47ad-ae77-a3878922fb43',
    '370cefb5-a8a4-4175-a012-8114be04b5af',
    'de5870b8-089d-469b-beef-0b85682ee7fe',
    '23154db6-eb06-4de2-984f-700570d37e20',
    'c4f2e791-046d-4325-821e-7844eccde5a1',
    'ceb10568-13c6-45fa-87f9-ef32b5e298b8',
    '599dd208-2bb2-46ac-95ea-29f78a78285a',
    '35ee0f3e-8eb0-4c40-8e57-6dd3b70bb55e',
    '3b6e4506-f655-4f84-86d9-50779a41dddb',
    '5daac571-61fa-4216-bb29-4a1ccf70b69a',
    'd64fe397-923d-4301-bb2d-622b5054aa80',
    '55e47dd4-c213-4680-bb55-30f7fe6c476b',
    '2b6dffba-dc86-4b2f-8b5a-2bdda354fab5',
    '8c860970-c5ca-4669-b947-5a1976d25cdf',
    'e6470415-dbcc-483e-9d53-6d361056d2d2',
    'aa8061b1-0c8b-4e50-97ed-82f4f71d43da',
    'ec4f46b6-2830-41ad-b803-9ba127b9da1b',
    '9b598401-b4d7-4dd5-b8b9-5327a592b6d5',
    '54c62a5b-c80f-44fa-b6c7-5bf83d415f79',
    'e28fcd99-12b1-4138-9b27-a3117dac10be',
    'eb6955e5-7cfa-4829-9e69-8c9306368a6d',
    'fb7e7b04-bbf8-4c36-9f71-0a11c1b0eb1d',
    '3e5a754d-08e8-429e-b350-23e2c3441adf'
  );
