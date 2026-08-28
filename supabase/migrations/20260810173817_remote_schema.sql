-- Migration unit 1: schema_changes
-- Transaction mode: transactional
-- Boundary reason: default

SET check_function_bodies = false;

DROP EXTENSION pg_net;

DROP EXTENSION pg_graphql;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT DELETE, INSERT, SELECT, UPDATE ON TABLES TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, USAGE ON SEQUENCES TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON ROUTINES TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT DELETE, INSERT, SELECT, UPDATE ON TABLES TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, USAGE ON SEQUENCES TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON ROUTINES TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT DELETE, INSERT, SELECT, UPDATE ON TABLES TO service_role;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT, USAGE ON SEQUENCES TO service_role;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT ALL ON ROUTINES TO service_role;

CREATE FUNCTION public.handle_new_user()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path TO 'public'
  AS $function$
BEGIN
    INSERT INTO public.user_profiles (id, full_name)
    VALUES (
        NEW.id,
        NEW.raw_user_meta_data->>'full_name'
    );
    RETURN NEW;
END;
$function$;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION public.handle_new_user();

REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC;

GRANT ALL ON FUNCTION public.handle_new_user() TO anon;

GRANT ALL ON FUNCTION public.handle_new_user() TO service_role;

CREATE FUNCTION public.rls_auto_enable()
  RETURNS event_trigger
  LANGUAGE plpgsql
  SET search_path TO 'pg_catalog'
  AS $function$
DECLARE
  cmd record;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table','partitioned table')
  LOOP
     IF cmd.schema_name IS NOT NULL AND cmd.schema_name IN ('public') AND cmd.schema_name NOT IN ('pg_catalog','information_schema') AND cmd.schema_name NOT LIKE 'pg_toast%' AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        EXECUTE format('alter table if exists %s enable row level security', cmd.object_identity);
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
     ELSE
        RAISE LOG 'rls_auto_enable: skip % (either system schema or not in enforced list: %.)', cmd.object_identity, cmd.schema_name;
     END IF;
  END LOOP;
END;
$function$;

REVOKE ALL ON FUNCTION public.rls_auto_enable() FROM PUBLIC;

GRANT ALL ON FUNCTION public.rls_auto_enable() TO anon;

GRANT ALL ON FUNCTION public.rls_auto_enable() TO service_role;

CREATE TABLE public.flood_alerts (
  id         uuid                     DEFAULT gen_random_uuid() NOT NULL,
  title      text                     NOT NULL,
  message    text                     NOT NULL,
  severity   text                     DEFAULT 'ADVISORY'::text,
  is_active  boolean                  DEFAULT true,
  created_at timestamp with time zone DEFAULT now()
);

ALTER TABLE public.flood_alerts
  ADD CONSTRAINT chk_alert_severity CHECK (severity = ANY (ARRAY['ADVISORY'::text, 'WATCH'::text, 'WARNING'::text, 'CRITICAL'::text]));

ALTER TABLE public.flood_alerts
  ADD CONSTRAINT flood_alerts_pkey PRIMARY KEY (id);

GRANT ALL ON public.flood_alerts TO anon;

GRANT ALL ON public.flood_alerts TO authenticated;

GRANT ALL ON public.flood_alerts TO service_role;

CREATE INDEX idx_flood_alerts_is_active ON public.flood_alerts (is_active);

CREATE POLICY "Authenticated can manage alerts" ON public.flood_alerts
  TO authenticated
  USING (true)
  WITH CHECK (true);

CREATE TABLE public.flood_reports (
  id          uuid                     DEFAULT gen_random_uuid() NOT NULL,
  user_id     uuid                     NOT NULL,
  image_url   text,
  address     text,
  latitude    double precision         NOT NULL,
  longitude   double precision         NOT NULL,
  flood_level text                     DEFAULT 'LOW'::text NOT NULL,
  description text,
  severity    integer                  DEFAULT 1,
  status      text                     DEFAULT 'PENDING'::text,
  created_at  timestamp with time zone DEFAULT now(),
  passability text
);

ALTER TABLE public.flood_reports
  ADD CONSTRAINT chk_flood_level CHECK (lower(flood_level) = ANY (ARRAY['low'::text, 'medium'::text, 'high'::text, 'critical'::text]));

ALTER TABLE public.flood_reports
  ADD CONSTRAINT chk_report_status CHECK (lower(status) = ANY (ARRAY['pending'::text, 'verified'::text, 'dismissed'::text, 'invalid_image'::text, 'invalid_information'::text]));

ALTER TABLE public.flood_reports
  ADD CONSTRAINT flood_reports_pkey PRIMARY KEY (id);

ALTER TABLE public.flood_reports
  ADD CONSTRAINT flood_reports_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

GRANT ALL ON public.flood_reports TO anon;

GRANT ALL ON public.flood_reports TO authenticated;

GRANT ALL ON public.flood_reports TO service_role;

CREATE INDEX idx_flood_reports_user_id ON public.flood_reports (user_id);

CREATE INDEX idx_flood_reports_created_at ON public.flood_reports (created_at DESC);

CREATE POLICY "Admin can update flood reports" ON public.flood_reports
  FOR UPDATE
  TO authenticated
  USING ((auth.email() = 'floodwatchkingsville@gmail.com'::text))
  WITH CHECK ((auth.email() = 'floodwatchkingsville@gmail.com'::text));

CREATE POLICY "Allow delete for admin only" ON public.flood_reports
  FOR DELETE
  TO authenticated
  USING ((auth.email() = 'floodwatchkingsville@gmail.com'::text));

CREATE POLICY "Authenticated can view all reports" ON public.flood_reports
  FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY "Authenticated users can insert reports" ON public.flood_reports
  FOR INSERT
  TO authenticated
  WITH CHECK ((auth.uid() = user_id));

CREATE POLICY "Enable insert for authenticated users only" ON public.flood_reports
  FOR INSERT
  TO authenticated
  WITH CHECK (true);

CREATE POLICY "Users can update own reports" ON public.flood_reports
  FOR UPDATE
  USING ((auth.uid() = user_id));

CREATE TABLE public.user_profiles (
  id                uuid                     NOT NULL,
  full_name         text,
  phone_number      text,
  address           text,
  profile_image_url text,
  created_at        timestamp with time zone DEFAULT now()
);

ALTER TABLE public.user_profiles
  ADD CONSTRAINT user_profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE public.user_profiles
  ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (id);

GRANT ALL ON public.user_profiles TO anon;

GRANT ALL ON public.user_profiles TO authenticated;

GRANT ALL ON public.user_profiles TO service_role;

CREATE POLICY "Users can insert their own profile" ON public.user_profiles
  FOR INSERT
  WITH CHECK ((auth.uid() = id));

CREATE POLICY "Users can update own profile" ON public.user_profiles
  FOR UPDATE
  USING ((auth.uid() = id));

CREATE POLICY "Users can update their own profile" ON public.user_profiles
  FOR UPDATE
  USING ((auth.uid() = id));

CREATE POLICY "Users can view own profile" ON public.user_profiles
  FOR SELECT
  USING ((auth.uid() = id));

CREATE POLICY "Users can view their own profile" ON public.user_profiles
  FOR SELECT
  USING ((auth.uid() = id));

CREATE EVENT TRIGGER ensure_rls
  ON ddl_command_end
  WHEN TAG IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
  EXECUTE FUNCTION public.rls_auto_enable();
